import { cleanup, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { forgetConversations } from '../src/pages/chats/conversations';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { ChatContactData, ChatEvent, MessageData } from '../src/types/chat';

// the live connection, faked: the test pushes events to whoever listens
const live = vi.hoisted(() => ({ listeners: new Map<string, (event: unknown) => void>() }));
vi.mock('../src/api/chatSocket', () => ({
  subscribeToChatEvents: (destination: string, onEvent: (event: unknown) => void) => {
    live.listeners.set(destination, onEvent);
    return () => live.listeners.delete(destination);
  },
}));

// these render the whole teacher area, which takes longer than the default second when every test file runs at once
configure({ asyncUtilTimeout: 5000 });
vi.setConfig({ testTimeout: 15000 });

const me: ChatContactData = { number: 9, name: 'Test Teacher', role: 'TEACHER' };
const ana: ChatContactData = { number: 50001, name: 'Test Ana', role: 'STUDENT' };
const CHAT = 7;
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

const message = (id: number, author: ChatContactData, text: string, sentAt: string, editedAt: string | null = null): MessageData =>
  ({ id, chatId: CHAT, author, text, sentAt, editedAt });

// the DM with Ana exists; `pages` answers each history request by the `before` it asks with (none for the first page)
function backend(pages: (before: string | null) => Response) {
  const calls: string[] = [];
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    calls.push(`${init?.method ?? 'GET'} ${url.pathname}${url.search}`);
    if (url.pathname === `/api/chats/direct/${ana.number}`) return reply({ chatId: CHAT, contact: ana });
    if (url.pathname === `/api/chats/${CHAT}/messages`) return pages(url.searchParams.get('before'));
    if (url.pathname === `/api/chats/${CHAT}/read`) return new Response(null, { status: 204 });
    throw new Error(`Unexpected request: ${url.pathname}`);
  }));
  return calls;
}

function open() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={[`/chats/direct/${ana.number}`]}><TeacherArea currentUser={{ ...me, institutionalEmail: 'teacher@school.pt', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

const log = async () => within(await screen.findByRole('log', { name: 'Messages' }));
const push = (event: ChatEvent) => live.listeners.get(`/topic/chats/${CHAT}`)?.(event);

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); forgetConversations(); live.listeners.clear(); });

describe('open a chat', () => {
  it('shows the newest messages oldest first, one name per run, and marks the chat as read', async () => {
    const calls = backend(() => reply({
      hasMore: false,
      messages: [
        message(1, ana, 'Hi, do you have a minute?', '2026-11-20T10:00:00'),
        message(2, ana, 'It is about the project.', '2026-11-20T10:01:00', '2026-11-20T10:02:00'),
        message(3, me, 'Sure, tell me.', '2026-11-20T10:05:00'),
      ],
    }));
    open();

    const messages = await log();

    const texts = messages.getAllByText(/minute|project|tell me/).map(node => node.firstChild?.textContent);
    expect(texts).toEqual(['Hi, do you have a minute?', 'It is about the project.', 'Sure, tell me.']);
    expect(messages.getAllByText('Test Ana')).toHaveLength(1);
    expect(messages.getByText('(edited)')).toBeTruthy();
    expect(screen.getByText('This is the start of your conversation with Test.')).toBeTruthy();
    await waitFor(() => expect(calls).toContain(`PUT /api/chats/${CHAT}/read`));
  });

  it('loads the 50 before the oldest one when you scroll up, until the first message', async () => {
    const calls = backend(before => before === null
      ? reply({ hasMore: true, messages: [message(60, ana, 'The newest one', '2026-11-21T09:00:00')] })
      : reply({ hasMore: false, messages: [message(10, ana, 'The very first one', '2026-11-19T09:00:00')] }));
    open();
    const messages = await log();
    expect(screen.queryByText(/start of your conversation/)).toBeNull();

    fireEvent.scroll(document.querySelector('.sm-chat__scroll')!);

    expect(await messages.findByText('The very first one')).toBeTruthy();
    expect(calls).toContain(`GET /api/chats/${CHAT}/messages?before=60`);
    expect(screen.getByText('This is the start of your conversation with Test.')).toBeTruthy();

    fireEvent.scroll(document.querySelector('.sm-chat__scroll')!);
    expect(calls.filter(call => call.startsWith(`GET /api/chats/${CHAT}/messages`))).toHaveLength(2);
  });

  it('with no messages yet, says so in the middle', async () => {
    backend(() => reply({ hasMore: false, messages: [] }));
    open();

    expect(await screen.findByText("Student · 50001. You haven't talked yet, say hi to Test.")).toBeTruthy();
  });

  it('shows a message that arrives while it is open, and marks it as read', async () => {
    const calls = backend(() => reply({ hasMore: false, messages: [message(1, ana, 'Hello', '2026-11-21T09:00:00')] }));
    open();
    const messages = await log();
    await waitFor(() => expect(calls.filter(call => call === `PUT /api/chats/${CHAT}/read`)).toHaveLength(1));

    push({ type: 'MESSAGE_CREATED', payload: message(2, ana, 'Are you there?', '2026-11-21T09:01:00') });

    expect(await messages.findByText('Are you there?')).toBeTruthy();
    await waitFor(() => expect(calls.filter(call => call === `PUT /api/chats/${CHAT}/read`)).toHaveLength(2));
  });

  it('offers New messages instead of jumping down while you read older ones', async () => {
    backend(() => reply({ hasMore: false, messages: [message(1, ana, 'Hello', '2026-11-21T09:00:00')] }));
    open();
    await log();
    const box = document.querySelector<HTMLDivElement>('.sm-chat__scroll')!;
    Object.defineProperty(box, 'scrollHeight', { configurable: true, value: 1000 });
    Object.defineProperty(box, 'clientHeight', { configurable: true, value: 300 });
    box.scrollTop = 200;
    fireEvent.scroll(box);

    push({ type: 'MESSAGE_CREATED', payload: message(2, ana, 'Look at this', '2026-11-21T09:01:00') });
    fireEvent.click(await screen.findByRole('button', { name: 'New messages' }));

    expect(screen.queryByRole('button', { name: 'New messages' })).toBeNull();
    expect(box.scrollTop).toBe(1000);
  });

  it('stops listening when you leave the chat', async () => {
    backend(() => reply({ hasMore: false, messages: [] }));
    open();
    await screen.findByText(/say hi/);
    expect(live.listeners.has(`/topic/chats/${CHAT}`)).toBe(true);

    cleanup();

    expect(live.listeners.has(`/topic/chats/${CHAT}`)).toBe(false);
  });

  it("shows the backend's error with a way back to the list", async () => {
    backend(() => reply({ code: 'NOT_FOUND' }, 404));
    open();

    expect(await screen.findByText(i18n.t('errors.NOT_FOUND'))).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Back to chats' })).toBeTruthy();
  });
});
