import { act, cleanup, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { forgetConversations } from '../src/pages/chats/conversations';
import { TYPING_SHOWN_MS, typingLine } from '../src/pages/chats/useTyping';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { ChatContactData, ChatEvent, ChatSummaryData, MessageData } from '../src/types/chat';

// the live connection, faked: what the test pushes reaches every listener, and the test says when it's up
const live = vi.hoisted(() => ({
  listeners: new Map<string, Set<(event: unknown) => void>>(),
  connected: true,
  onConnection: new Set<() => void>(),
  typing: [] as number[],
}));
vi.mock('../src/api/chatSocket', () => ({ onChatSocketReconnect: () => () => undefined,
  isChatSocketConnected: () => live.connected,
  onChatSocketConnection: (listener: () => void) => { live.onConnection.add(listener); return () => live.onConnection.delete(listener); },
  sendTyping: (chatId: number) => live.typing.push(chatId),
  subscribeToChatEvents: (destination: string, onEvent: (event: unknown) => void) => {
    const set = live.listeners.get(destination) ?? new Set();
    set.add(onEvent);
    live.listeners.set(destination, set);
    return () => set.delete(onEvent);
  },
}));

configure({ asyncUtilTimeout: 5000 });
vi.setConfig({ testTimeout: 15000 });

const me: ChatContactData = { number: 9, name: 'Test Teacher', role: 'TEACHER' };
const ana: ChatContactData = { number: 50001, name: 'Test Ana Silva', role: 'STUDENT' };
const rui: ChatContactData = { number: 50002, name: 'Test Rui', role: 'STUDENT' };
const CHAT = 7;
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
const message = (id: number, author: ChatContactData, text: string): MessageData =>
  ({ id, chatId: CHAT, author, text, sentAt: '2026-11-20T10:00:00', editedAt: null, reply: false, replyTo: null });
const dm = (contact: ChatContactData, chatId = CHAT): ChatSummaryData =>
  ({ chatId, name: contact.name, contact, picture: false, pictureUpdatedAt: null, lastMessage: message(chatId * 10, contact, 'Hi'), unread: 0, assignment: false });

// the DM with Ana and one with Rui; `online` answers each GET /chats/online in turn
function backend(online: number[][] = [[]]) {
  const calls: string[] = [];
  let onlineCall = 0;
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async input => {
    const url = new URL(String(input), 'https://test.invalid');
    calls.push(url.pathname);
    if (url.pathname === '/api/chats/online') return reply(online[Math.min(onlineCall++, online.length - 1)]);
    if (url.pathname === `/api/chats/direct/${ana.number}`) return reply({ chatId: CHAT, contact: ana, blocked: false });
    if (url.pathname === `/api/chats/${CHAT}/messages`) return reply({ hasMore: false, messages: [message(1, ana, 'Hi there')], reactions: [] });
    if (url.pathname === `/api/chats/${CHAT}/read`) return new Response(null, { status: 204 });
    if (url.pathname === '/api/chats/') return reply([dm(ana), dm(rui, 8)]);
    if (url.pathname === '/api/chats/unread-count') return reply(0);
    throw new Error(`Unexpected request: ${url.pathname}`);
  }));
  return calls;
}

function open(path = `/chats/direct/${ana.number}`) {
  render(<StudyMatchProvider><MemoryRouter initialEntries={[path]}><TeacherArea currentUser={{ ...me, institutionalEmail: 'teacher@school.pt', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

async function push(destination: string, event: ChatEvent) {
  await waitFor(() => expect(live.listeners.get(destination)?.size).toBeGreaterThan(0));
  act(() => live.listeners.get(destination)?.forEach(listener => listener(event)));
}
const typingIn = (user: ChatContactData): ChatEvent => ({ type: 'TYPING', payload: { chatId: CHAT, user } });
const typingLineText = () => document.querySelector('.sm-chat__typing')?.textContent ?? '';
const row = async (name: string) => (await within(await screen.findByRole('list', { name: 'Chats' })).findByText(name, { selector: '.sm-chat__row-name' })).closest('a')!;
const dotOn = (element: Element | null) => element?.querySelector('.sm-avatar__presence') !== null;
const setConnected = (value: boolean) => act(() => { live.connected = value; live.onConnection.forEach(listener => listener()); });

beforeEach(async () => { await i18n.changeLanguage('en'); live.connected = true; live.typing = []; });
afterEach(() => { cleanup(); vi.useRealTimers(); vi.unstubAllGlobals(); vi.restoreAllMocks(); forgetConversations(); live.listeners.clear(); live.onConnection.clear(); });

describe('typing', () => {
  it('shows who is typing until their message comes', async () => {
    backend();
    open();
    await screen.findByText('Hi there');

    await push(`/topic/chats/${CHAT}`, typingIn(ana));
    expect(typingLineText()).toBe('Test is typing…');

    await push(`/topic/chats/${CHAT}`, { type: 'MESSAGE_CREATED', payload: message(2, ana, 'Done') });
    await waitFor(() => expect(typingLineText()).toBe(''));
  });

  it('stops showing it 5 seconds after the last one', async () => {
    backend();
    open();
    await screen.findByText('Hi there');
    vi.useFakeTimers({ shouldAdvanceTime: true });

    await push(`/topic/chats/${CHAT}`, typingIn(ana));
    act(() => { vi.advanceTimersByTime(TYPING_SHOWN_MS - 1000); });
    // another one starts the 5 seconds again
    await push(`/topic/chats/${CHAT}`, typingIn(ana));
    act(() => { vi.advanceTimersByTime(TYPING_SHOWN_MS - 1000); });
    expect(typingLineText()).toBe('Test is typing…');

    act(() => { vi.advanceTimersByTime(1000); });
    expect(typingLineText()).toBe('');
  });

  it('never shows you typing, from your other tab', async () => {
    backend();
    open();
    await screen.findByText('Hi there');

    await push(`/topic/chats/${CHAT}`, typingIn(me));

    expect(typingLineText()).toBe('');
  });

  it('says one, two or several people are typing', () => {
    const t = i18n.t.bind(i18n);
    expect(typingLine([], t)).toBeUndefined();
    expect(typingLine([ana], t)).toBe('Test is typing…');
    expect(typingLine([ana, rui], t)).toBe('Test and Test are typing…');
    expect(typingLine([ana, rui, me], t)).toBe('Several people are typing…');
  });

  it('tells the others you are typing at most every few seconds, not on every key', async () => {
    backend();
    open();
    const box = await screen.findByRole('textbox', { name: 'Message Test Ana Silva' });

    fireEvent.change(box, { target: { value: 'H' } });
    fireEvent.change(box, { target: { value: 'He' } });
    fireEvent.change(box, { target: { value: 'Hel' } });

    expect(live.typing).toEqual([CHAT]);
  });
});

describe('online', () => {
  it('puts a green dot on the people who have the app open, in the list and in the chat', async () => {
    backend([[ana.number]]);
    open();

    await waitFor(async () => expect(dotOn(await row('Test Ana Silva'))).toBe(true));
    expect((await row('Test Ana Silva')).textContent).toContain('Online');
    expect(dotOn(await row('Test Rui'))).toBe(false);
    expect(document.querySelector('.sm-chat__head-sub--online')?.textContent).toBe('Online');
  });

  it('follows USER_ONLINE and USER_OFFLINE', async () => {
    backend([[ana.number]]);
    open();
    await waitFor(async () => expect(dotOn(await row('Test Ana Silva'))).toBe(true));

    await push('/user/queue/chats', { type: 'USER_ONLINE', payload: rui.number });
    await push('/user/queue/chats', { type: 'USER_OFFLINE', payload: ana.number });

    await waitFor(async () => expect(dotOn(await row('Test Rui'))).toBe(true));
    expect(dotOn(await row('Test Ana Silva'))).toBe(false);
    expect(document.querySelector('.sm-chat__head-sub--online')).toBeNull();
  });

  it('asks again for the people of a chat you were just added to', async () => {
    const calls = backend([[], [rui.number]]);
    open();
    await waitFor(() => expect(calls.filter(path => path === '/api/chats/online')).toHaveLength(1));

    await push('/user/queue/chats', { type: 'CHAT_ADDED', payload: dm(rui, 8) });

    await waitFor(async () => expect(dotOn(await row('Test Rui'))).toBe(true));
  });

  it('takes every dot away while the connection is down, and asks again when it is back', async () => {
    const calls = backend([[ana.number], [rui.number]]);
    open();
    await waitFor(async () => expect(dotOn(await row('Test Ana Silva'))).toBe(true));

    setConnected(false);
    expect(dotOn(await row('Test Ana Silva'))).toBe(false);

    setConnected(true);
    await waitFor(async () => expect(dotOn(await row('Test Rui'))).toBe(true));
    expect(dotOn(await row('Test Ana Silva'))).toBe(false);
    expect(calls.filter(path => path === '/api/chats/online')).toHaveLength(2);
  });
});
