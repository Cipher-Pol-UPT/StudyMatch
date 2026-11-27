import { act, cleanup, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { forgetConversations } from '../src/pages/chats/conversations';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { ChatContactData, ChatEvent, MessageData, MessageQuoteData } from '../src/types/chat';

// every listener of a destination gets what the test pushes
const live = vi.hoisted(() => ({ listeners: new Map<string, Set<(event: unknown) => void>>() }));
vi.mock('../src/api/chatSocket', () => ({
  // the connection is down, so nobody shows as online
  isChatSocketConnected: () => false, onChatSocketConnection: () => () => undefined, sendTyping: vi.fn(),
  subscribeToChatEvents: (destination: string, onEvent: (event: unknown) => void) => {
    const set = live.listeners.get(destination) ?? new Set();
    set.add(onEvent);
    live.listeners.set(destination, set);
    return () => set.delete(onEvent);
  },
}));

// these render the whole teacher area, which takes longer than the default second when every test file runs at once
configure({ asyncUtilTimeout: 5000 });
vi.setConfig({ testTimeout: 15000 });

const me: ChatContactData = { number: 9, name: 'Test Teacher', role: 'TEACHER' };
const ana: ChatContactData = { number: 50001, name: 'Test Ana', role: 'STUDENT' };
const CHAT = 7;
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

const quote = (original: MessageData): MessageQuoteData => ({ id: original.id, author: original.author, text: original.text });
const message = (id: number, author: ChatContactData, text: string, replyTo?: MessageData | null): MessageData => ({
  id, chatId: CHAT, author, text, sentAt: `2026-11-21T09:${String(id % 60).padStart(2, '0')}:00`, editedAt: null,
  reply: replyTo !== undefined, replyTo: replyTo ? quote(replyTo) : null,
});

interface Call { method: string; path: string; body?: unknown }

// the DM with Ana exists. `answer` takes the requests the test cares about; the rest are the chat list's and reading
function backend(answer: (call: Call, url: URL) => Response | undefined) {
  const calls: Call[] = [];
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    const call = { method: init?.method ?? 'GET', path: `${url.pathname}${url.search}`, body: init?.body ? JSON.parse(String(init.body)) : undefined };
    calls.push(call);
    const answered = answer(call, url);
    if (answered) return answered;
    if (url.pathname === `/api/chats/direct/${ana.number}`) return json({ chatId: CHAT, contact: ana, blocked: false });
    if (url.pathname === `/api/chats/${CHAT}/read`) return new Response(null, { status: 204 });
    if (url.pathname === '/api/chats/') return json([]);
    if (url.pathname === '/api/chats/unread-count') return json(0);
    throw new Error(`Unexpected request: ${call.method} ${call.path}`);
  }));
  return calls;
}

// the newest page is `messages`, the whole chat for these tests
const newest = (messages: MessageData[], more: Partial<{ hasMore: boolean }> = {}) => (call: Call) =>
  call.method === 'GET' && call.path === `/api/chats/${CHAT}/messages` ? json({ messages, reactions: [], hasMore: false, hasNewer: false, ...more }) : undefined;

function open() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={[`/chats/direct/${ana.number}`]}><TeacherArea currentUser={{ ...me, institutionalEmail: 'teacher@school.pt', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

const log = async () => within(await screen.findByRole('log', { name: 'Messages' }));
const composer = () => screen.getByRole('textbox', { name: 'Message Test Ana' });
// the row of a message, found by the text in its bubble (a quote of it can have the same text)
const rowOf = (text: string) => [...document.querySelectorAll('.sm-msg__bubble')].find(bubble => bubble.firstChild?.textContent === text)!.closest('.sm-msg') as HTMLElement;
const push = (event: ChatEvent) => live.listeners.get(`/topic/chats/${CHAT}`)?.forEach(listener => listener(event));

beforeEach(async () => {
  await i18n.changeLanguage('en');
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); forgetConversations(); live.listeners.clear(); });

describe('reply to a message', () => {
  const question = message(1, ana, 'Can you check my report?');

  it('shows who you are replying to above the message box, and sends the reply with its quote', async () => {
    const sent = { ...message(2, me, 'Sure, tonight'), reply: true, replyTo: quote(question) };
    const calls = backend((call, url) => newest([question])(call)
      ?? (call.method === 'POST' && url.pathname === `/api/chats/direct/${ana.number}/messages` ? json(sent, 201) : undefined));
    open();
    await log();

    fireEvent.click(within(rowOf('Can you check my report?')).getByRole('button', { name: 'Reply' }));

    expect(document.querySelector('.sm-composer__bar')?.textContent).toBe('Replying to Test Ana · Can you check my report?');
    expect(document.activeElement).toBe(composer());

    fireEvent.change(composer(), { target: { value: 'Sure, tonight' } });
    fireEvent.keyDown(composer(), { key: 'Enter' });

    await waitFor(() => expect(calls.find(call => call.method === 'POST')?.body).toEqual({ text: 'Sure, tonight', replyToId: 1 }));
    expect(document.querySelector('.sm-composer__bar')).toBeNull();
    // the reply quotes one line of the original above it
    await waitFor(() => expect(rowOf('Sure, tonight').querySelector('.sm-msg__quote')?.textContent).toBe('Test AnaCan you check my report?'));
  });

  it('stops replying with Esc in the message box, or with the X', async () => {
    backend(newest([question]));
    open();
    await log();

    fireEvent.click(within(rowOf('Can you check my report?')).getByRole('button', { name: 'Reply' }));
    fireEvent.keyDown(composer(), { key: 'Escape' });
    expect(document.querySelector('.sm-composer__bar')).toBeNull();

    fireEvent.click(within(rowOf('Can you check my report?')).getByRole('button', { name: 'Reply' }));
    fireEvent.click(screen.getByRole('button', { name: 'Cancel reply' }));
    expect(document.querySelector('.sm-composer__bar')).toBeNull();
    expect(document.activeElement).toBe(composer());
  });

  it('jumps to an original that is on screen without asking for it again, and highlights it', async () => {
    const answer = message(2, me, 'Sure, tonight', question);
    const calls = backend(newest([question, answer]));
    open();
    await log();

    fireEvent.click(screen.getByRole('button', { name: "Go to Test Ana's message" }));

    await waitFor(() => expect(rowOf('Can you check my report?').classList).toContain('sm-msg--flash'));
    expect(calls.some(call => call.path.includes('/around'))).toBe(false);
  });

  it('loads the messages around an original that is not on screen, then the newer ones as you scroll down', async () => {
    const old = message(5, ana, 'The very old question');
    const answer = message(80, me, 'Answering that old one', old);
    const calls = backend((call, url) => {
      if (url.pathname === `/api/chats/${CHAT}/messages/5/around`) return json({ messages: [old, message(6, ana, 'Right after it')], reactions: [], hasMore: true, hasNewer: true });
      if (call.path === `/api/chats/${CHAT}/messages?after=6`) return json({ messages: [answer], reactions: [], hasMore: true, hasNewer: false });
      return newest([answer], { hasMore: true })(call);
    });
    open();
    const messages = await log();

    fireEvent.click(screen.getByRole('button', { name: "Go to Test Ana's message" }));

    expect(await messages.findByText('The very old question')).toBeTruthy();
    await waitFor(() => expect(rowOf('The very old question').classList).toContain('sm-msg--flash'));
    // the latest aren't loaded now, so scrolling down brings them back
    expect(messages.queryByText('Answering that old one')).toBeNull();

    fireEvent.scroll(document.querySelector('.sm-chat__scroll')!);

    expect(await messages.findByText('Answering that old one')).toBeTruthy();
    expect(calls.map(call => call.path)).toContain(`/api/chats/${CHAT}/messages?after=6`);
  });

  it('shows Back to the latest after a jump, even with nothing new, and it brings the latest back', async () => {
    const old = message(5, ana, 'The very old question');
    const answer = message(80, me, 'Answering that old one', old);
    backend((call, url) => {
      if (url.pathname === `/api/chats/${CHAT}/messages/5/around`) return json({ messages: [old], reactions: [], hasMore: true, hasNewer: true });
      if (call.path.includes('after=')) return json({ messages: [], reactions: [], hasMore: true, hasNewer: true });
      return newest([answer], { hasMore: true })(call);
    });
    open();
    const messages = await log();
    expect(screen.queryByRole('button', { name: 'Back to the latest' })).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: "Go to Test Ana's message" }));
    await messages.findByText('The very old question');
    fireEvent.click(await screen.findByRole('button', { name: 'Back to the latest' }));

    expect(await (await log()).findByText('Answering that old one')).toBeTruthy();
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Back to the latest' })).toBeNull());
  });

  it('holds a new message back while the latest are not loaded, and New messages brings them back', async () => {
    const old = message(5, ana, 'The very old question');
    const answer = message(80, me, 'Answering that old one', old);
    let latest = [answer];
    const calls = backend((call, url) => {
      if (url.pathname === `/api/chats/${CHAT}/messages/5/around`) return json({ messages: [old], reactions: [], hasMore: true, hasNewer: true });
      if (call.path.includes('after=')) return json({ messages: [], reactions: [], hasMore: true, hasNewer: true });
      return newest(latest, { hasMore: true })(call);
    });
    open();
    const messages = await log();
    fireEvent.click(screen.getByRole('button', { name: "Go to Test Ana's message" }));
    await messages.findByText('The very old question');

    const arrived = message(81, ana, 'Did you see it?');
    latest = [answer, arrived];
    const reads = () => calls.filter(call => call.method === 'PUT').length;
    const readBefore = reads();
    act(() => push({ type: 'MESSAGE_CREATED', payload: arrived }));

    // not on screen, so not read either
    expect(messages.queryByText('Did you see it?')).toBeNull();
    fireEvent.click(await screen.findByRole('button', { name: 'New messages' }));
    expect(reads()).toBe(readBefore);

    // the latest replace the old ones on screen
    expect(await (await log()).findByText('Did you see it?')).toBeTruthy();
    await waitFor(() => expect(reads()).toBe(readBefore + 1));
    // only its quote is left
    expect(document.getElementById('message-5')).toBeNull();
  });

  it('says the original was deleted, without a way to go to it', async () => {
    backend(newest([message(2, ana, 'About what you said', null)]));
    open();
    await log();

    const gone = rowOf('About what you said').querySelector('.sm-msg__quote--gone');
    expect(gone?.textContent).toBe('Original message was deleted');
    expect(gone?.tagName).toBe('SPAN');
  });

  it('updates or drops the quote when the original is edited or deleted while the chat is open', async () => {
    const answer = message(2, me, 'Sure, tonight', question);
    backend(newest([question, answer]));
    open();
    await log();

    act(() => push({ type: 'MESSAGE_EDITED', payload: { ...question, text: 'Can you check my final report?', editedAt: '2026-11-21T10:00:00' } }));
    await waitFor(() => expect(rowOf('Sure, tonight').querySelector('.sm-msg__quote')?.textContent).toBe('Test AnaCan you check my final report?'));

    act(() => push({ type: 'MESSAGE_DELETED', payload: { chatId: CHAT, messageId: 1 } }));
    await waitFor(() => expect(rowOf('Sure, tonight').querySelector('.sm-msg__quote')?.textContent).toBe('Original message was deleted'));
  });

  it('stops the reply when the message you are answering is deleted', async () => {
    backend(newest([question]));
    open();
    await log();
    fireEvent.click(within(rowOf('Can you check my report?')).getByRole('button', { name: 'Reply' }));

    act(() => push({ type: 'MESSAGE_DELETED', payload: { chatId: CHAT, messageId: 1 } }));

    await waitFor(() => expect(document.querySelector('.sm-composer__bar')).toBeNull());
  });

  it('shows under the message box when the original is gone, and Retry sends it as a plain message', async () => {
    let refuse = true;
    const calls = backend((call, url) => {
      if (call.method === 'POST' && url.pathname === `/api/chats/direct/${ana.number}/messages`) {
        return refuse ? json({ code: 'REPLIED_MESSAGE_NOT_FOUND' }, 404) : json(message(3, me, 'Sure, tonight'), 201);
      }
      return newest([question])(call);
    });
    open();
    await log();
    fireEvent.click(within(rowOf('Can you check my report?')).getByRole('button', { name: 'Reply' }));
    fireEvent.change(composer(), { target: { value: 'Sure, tonight' } });
    fireEvent.keyDown(composer(), { key: 'Enter' });

    expect(await screen.findByText("The message you're replying to isn't there anymore.")).toBeTruthy();

    refuse = false;
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));

    await waitFor(() => expect(calls.filter(call => call.method === 'POST').at(-1)?.body).toEqual({ text: 'Sure, tonight' }));
  });

  it('tells you when the original was deleted before you could go to it', async () => {
    const old = message(5, ana, 'The very old question');
    backend((call, url) => url.pathname === `/api/chats/${CHAT}/messages/5/around`
      ? json({ code: 'NOT_FOUND' }, 404)
      : newest([message(80, me, 'Answering that old one', old)], { hasMore: true })(call));
    open();
    await log();

    fireEvent.click(screen.getByRole('button', { name: "Go to Test Ana's message" }));

    expect(await screen.findByText("The original message isn't there anymore.")).toBeTruthy();
  });
});
