import { act, cleanup, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { forgetConversations } from '../src/pages/chats/conversations';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { ChatContactData, ChatEvent, MessageData } from '../src/types/chat';

// every listener of a destination gets what the test pushes: the open chat, the chat list and the side menu all listen
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
    // the chat list and the side menu's count, both empty here
    if (url.pathname === '/api/chats/') return new Response('[]', { status: 200 });
    if (url.pathname === '/api/chats/unread-count') return new Response('0', { status: 200 });
    throw new Error(`Unexpected request: ${url.pathname}`);
  }));
  return calls;
}

function open() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={[`/chats/direct/${ana.number}`]}><TeacherArea currentUser={{ ...me, institutionalEmail: 'teacher@school.pt', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

const log = async () => within(await screen.findByRole('log', { name: 'Messages' }));
const listening = (destination: string) => (live.listeners.get(destination)?.size ?? 0) > 0;
const push = (event: ChatEvent) => live.listeners.get(`/topic/chats/${CHAT}`)?.forEach(listener => listener(event));
// the chat list's queue, once something listens to it: on a slow machine the page can show before it subscribes
async function pushToList(event: unknown) {
  await waitFor(() => expect(listening('/user/queue/chats')).toBe(true));
  live.listeners.get('/user/queue/chats')?.forEach(listener => listener(event));
}
const settle = () => act(() => new Promise(resolve => setTimeout(resolve, 50)));

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); forgetConversations(); live.listeners.clear(); });

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

  it('loads the older ones by itself when the first page is too short to scroll', async () => {
    vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(300);
    const calls = backend(before => before === null
      ? reply({ hasMore: true, messages: [message(60, ana, 'The newest one', '2026-11-21T09:00:00')] })
      : reply({ hasMore: false, messages: [message(10, ana, 'The very first one', '2026-11-19T09:00:00')] }));
    open();

    expect(await screen.findByText('The very first one', {}, { timeout: 1500 })).toBeTruthy();
    expect(calls).toContain(`GET /api/chats/${CHAT}/messages?before=60`);
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
    expect(listening(`/topic/chats/${CHAT}`)).toBe(true);

    cleanup();

    expect(listening(`/topic/chats/${CHAT}`)).toBe(false);
  });

  it("shows the backend's error with a way back to the list", async () => {
    backend(() => reply({ code: 'NOT_FOUND' }, 404));
    open();

    expect(await screen.findByText(i18n.t('errors.NOT_FOUND'))).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Back to chats' })).toBeTruthy();
  });
});

// the DM with one message of yours and one of Ana's; edits and deletes answer as `change` says
function backendWithChanges(change: (method: string, id: number, body: unknown) => Response) {
  const calls: string[] = [];
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    const method = init?.method ?? 'GET';
    calls.push(`${method} ${url.pathname}`);
    if (url.pathname === `/api/chats/direct/${ana.number}`) return reply({ chatId: CHAT, contact: ana, blocked: false });
    if (url.pathname === `/api/chats/${CHAT}/messages`) {
      return reply({ hasMore: false, messages: [message(1, ana, 'Did you finish it?', '2026-11-21T09:00:00'), message(2, me, 'Almost done', '2026-11-21T09:01:00')] });
    }
    if (url.pathname === `/api/chats/${CHAT}/read`) return new Response(null, { status: 204 });
    const own = url.pathname.match(new RegExp(`^/api/chats/${CHAT}/messages/(\\d+)$`));
    if (own) return change(method, Number(own[1]), init?.body ? JSON.parse(String(init.body)) : undefined);
    // the chat list and the side menu's count, both empty here
    if (url.pathname === '/api/chats/') return new Response('[]', { status: 200 });
    if (url.pathname === '/api/chats/unread-count') return new Response('0', { status: 200 });
    throw new Error(`Unexpected request: ${method} ${url.pathname}`);
  }));
  return calls;
}

const edited = (text: string) => reply(message(2, me, text, '2026-11-21T09:01:00', '2026-11-21T09:03:00'));
const gone = () => new Response(null, { status: 204 });

describe('edit a message', () => {
  it('saves the new text with Enter and shows "(edited)"', async () => {
    const calls = backendWithChanges((_, __, body) => edited((body as { text: string }).text));
    open();
    const messages = await log();
    expect(within(messages.getByText('Did you finish it?').closest('.sm-msg') as HTMLElement).queryByRole('button', { name: 'Edit' })).toBeNull();

    fireEvent.click(within(messages.getByText('Almost done').closest('.sm-msg') as HTMLElement).getByRole('button', { name: 'Edit' }));
    const box = screen.getByRole('textbox', { name: 'Edit message' });
    fireEvent.change(box, { target: { value: 'All done' } });
    fireEvent.keyDown(box, { key: 'Enter' });

    expect(await messages.findByText('All done')).toBeTruthy();
    expect(messages.getByText('(edited)')).toBeTruthy();
    expect(calls).toContain(`PUT /api/chats/${CHAT}/messages/2`);
  });

  it('puts the old text back with Esc', async () => {
    const calls = backendWithChanges(() => edited('never'));
    open();
    const messages = await log();
    fireEvent.click(within(messages.getByText('Almost done').closest('.sm-msg') as HTMLElement).getByRole('button', { name: 'Edit' }));
    const box = screen.getByRole('textbox', { name: 'Edit message' });
    fireEvent.change(box, { target: { value: 'Something else' } });

    fireEvent.keyDown(box, { key: 'Escape' });

    expect(screen.queryByRole('textbox', { name: 'Edit message' })).toBeNull();
    expect(messages.getByText('Almost done')).toBeTruthy();
    expect(calls).not.toContain(`PUT /api/chats/${CHAT}/messages/2`);
  });

  it('edits your last message with Arrow up in an empty message box', async () => {
    backendWithChanges(() => edited('never'));
    open();
    await log();

    fireEvent.keyDown(screen.getByRole('textbox', { name: 'Message Test Ana' }), { key: 'ArrowUp' });

    expect((screen.getByRole('textbox', { name: 'Edit message' }) as HTMLTextAreaElement).value).toBe('Almost done');
  });

  it("shows the backend's error under the box", async () => {
    backendWithChanges(() => reply({ code: 'NOT_MESSAGE_AUTHOR' }, 403));
    open();
    const messages = await log();
    fireEvent.click(within(messages.getByText('Almost done').closest('.sm-msg') as HTMLElement).getByRole('button', { name: 'Edit' }));
    const box = screen.getByRole('textbox', { name: 'Edit message' });
    fireEvent.change(box, { target: { value: 'All done' } });

    fireEvent.keyDown(box, { key: 'Enter' });

    expect((await screen.findByRole('alert')).textContent).toBe(i18n.t('errors.NOT_MESSAGE_AUTHOR'));
  });
});

describe('delete a message', () => {
  const deleteButton = (messages: ReturnType<typeof within>) =>
    within(messages.getByText('Almost done').closest('.sm-msg') as HTMLElement).getByRole('button', { name: 'Delete' });

  it('asks first, with the message, then removes it', async () => {
    const calls = backendWithChanges(gone);
    open();
    const messages = await log();

    fireEvent.click(deleteButton(messages));
    const dialog = within(await screen.findByRole('dialog', { name: 'Delete this message?' }));
    expect(dialog.getByText('Almost done')).toBeTruthy();
    fireEvent.click(dialog.getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(messages.queryByText('Almost done')).toBeNull());
    expect(calls).toContain(`DELETE /api/chats/${CHAT}/messages/2`);
  });

  it('keeps it when you cancel', async () => {
    const calls = backendWithChanges(gone);
    open();
    const messages = await log();

    fireEvent.click(deleteButton(messages));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: i18n.t('ui.cancel') }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(messages.getByText('Almost done')).toBeTruthy();
    expect(calls.some(call => call.startsWith('DELETE'))).toBe(false);
  });

  it('skips the question when Shift is held', async () => {
    const calls = backendWithChanges(gone);
    open();
    const messages = await log();

    fireEvent.click(deleteButton(messages), { shiftKey: true });

    await waitFor(() => expect(messages.queryByText('Almost done')).toBeNull());
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(calls).toContain(`DELETE /api/chats/${CHAT}/messages/2`);
  });
});

describe('changes from the other side, while the chat is open', () => {
  it('shows an edit and takes a deleted message away', async () => {
    backendWithChanges(gone);
    open();
    const messages = await log();

    push({ type: 'MESSAGE_EDITED', payload: message(1, ana, 'Did you finish the report?', '2026-11-21T09:00:00', '2026-11-21T09:02:00') });
    expect(await messages.findByText('Did you finish the report?')).toBeTruthy();

    push({ type: 'MESSAGE_DELETED', payload: { chatId: CHAT, messageId: 1 } });
    await waitFor(() => expect(messages.queryByText('Did you finish the report?')).toBeNull());
  });
});

// a DM that exists (or not yet, `chatId` null) with `history` in it; sending waits for `send` to answer
// `chatId` is the DM's id each time it's asked for, so a test can create the DM halfway through
function liveBackend({ chatId = (() => CHAT) as () => number | null, history = [] as MessageData[], send = () => new Promise<Response>(() => {}), edit = () => new Promise<Response>(() => {}) } = {}) {
  const calls: string[] = [];
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    const method = init?.method ?? 'GET';
    calls.push(`${method} ${url.pathname}${url.search}`);
    if (url.pathname === `/api/chats/direct/${ana.number}`) return reply({ chatId: chatId(), contact: ana, blocked: false });
    if (url.pathname === `/api/chats/direct/${ana.number}/messages`) return send();
    if (url.pathname === `/api/chats/${CHAT}/messages`) return reply({ hasMore: url.searchParams.has('before') ? false : history.length > 1, messages: url.searchParams.has('before') ? [] : history });
    if (url.pathname === `/api/chats/${CHAT}/read`) return new Response(null, { status: 204 });
    if (url.pathname.startsWith(`/api/chats/${CHAT}/messages/`)) return edit();
    // the chat list and the side menu's count, both empty here
    if (url.pathname === '/api/chats/') return new Response('[]', { status: 200 });
    if (url.pathname === '/api/chats/unread-count') return new Response('0', { status: 200 });
    throw new Error(`Unexpected request: ${method} ${url.pathname}`);
  }));
  return calls;
}

const reads = (calls: string[]) => calls.filter(call => call === `PUT /api/chats/${CHAT}/read`).length;

describe('while a chat is open', () => {
  it("ignores an edit to a message that isn't loaded, so older ones still load from the oldest shown", async () => {
    const calls = liveBackend({ history: [message(60, ana, 'Newest', '2026-11-21T09:00:00'), message(61, ana, 'Newer', '2026-11-21T09:01:00')] });
    open();
    const messages = await log();

    await messages.findByText('Newest');
    push({ type: 'MESSAGE_EDITED', payload: message(5, ana, 'From months ago', '2026-01-10T09:00:00', '2026-11-21T09:05:00') });

    // scrolled again until it asks, a scroll while the first page is still settling loads nothing
    await waitFor(() => {
      fireEvent.scroll(document.querySelector('.sm-chat__scroll')!);
      expect(calls).toContain(`GET /api/chats/${CHAT}/messages?before=60`);
    });
    expect(messages.queryByText('From months ago')).toBeNull();
  });

  it('loads the DM once they write first while it is open and still empty', async () => {
    let dm: number | null = null;
    liveBackend({ chatId: () => dm, history: [message(1, ana, 'Hi there', '2026-11-21T09:00:00')] });
    open();
    await screen.findByText(/say hi/);

    dm = CHAT;
    await pushToList({ type: 'CHAT_UPDATED', payload: message(1, ana, 'Hi there', '2026-11-21T09:00:00') });

    expect(await (await log()).findByText('Hi there')).toBeTruthy();
  });

  it('marks a message that came while the tab was hidden as read once you come back', async () => {
    const calls = liveBackend({ history: [message(1, ana, 'Hello', '2026-11-21T09:00:00')] });
    open();
    await log();
    await waitFor(() => expect(reads(calls)).toBe(1));
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');

    push({ type: 'MESSAGE_CREATED', payload: message(2, ana, 'Still there?', '2026-11-21T09:01:00') });
    expect(await screen.findByText('Still there?')).toBeTruthy();
    expect(reads(calls)).toBe(1);

    visibility.mockReturnValue('visible');
    fireEvent(document, new Event('visibilitychange'));

    await waitFor(() => expect(reads(calls)).toBe(2));
    visibility.mockRestore();
  });

  it('shows your message once when its live event comes before the answer to sending it', async () => {
    let answer: (response: Response) => void = () => {};
    liveBackend({ history: [message(1, ana, 'Hello', '2026-11-21T09:00:00')], send: () => new Promise(resolve => { answer = resolve; }) });
    open();
    const messages = await log();
    const field = screen.getByRole('textbox', { name: 'Message Test Ana' });
    fireEvent.change(field, { target: { value: 'On my way' } });
    fireEvent.keyDown(field, { key: 'Enter' });
    expect(await messages.findByText('Sending…')).toBeTruthy();

    push({ type: 'MESSAGE_CREATED', payload: message(2, me, 'On my way', '2026-11-21T09:02:00') });
    await waitFor(() => expect(messages.queryByText('Sending…')).toBeNull());
    expect(messages.getAllByText('On my way')).toHaveLength(1);

    answer(reply(message(2, me, 'On my way', '2026-11-21T09:02:00'), 201));
    await settle();
    expect(messages.getAllByText('On my way')).toHaveLength(1);
  });

  it('saves an edit once when Enter is pressed twice', async () => {
    const calls = liveBackend({ history: [message(1, ana, 'Hello', '2026-11-21T09:00:00'), message(2, me, 'Almost done', '2026-11-21T09:01:00')] });
    open();
    const messages = await log();
    fireEvent.click(within(messages.getByText('Almost done').closest('.sm-msg') as HTMLElement).getByRole('button', { name: 'Edit' }));
    const box = screen.getByRole('textbox', { name: 'Edit message' });
    fireEvent.change(box, { target: { value: 'All done' } });

    fireEvent.keyDown(box, { key: 'Enter' });
    fireEvent.keyDown(box, { key: 'Enter' });
    await settle();

    expect(calls.filter(call => call === `PUT /api/chats/${CHAT}/messages/2`)).toHaveLength(1);
  });

  it('starts a new run with its time when you write right after them', async () => {
    liveBackend({ history: [message(1, ana, 'Hello', new Date().toISOString().slice(0, 19))] });
    open();
    const messages = await log();
    const field = screen.getByRole('textbox', { name: 'Message Test Ana' });
    fireEvent.change(field, { target: { value: 'Hi Ana' } });
    fireEvent.keyDown(field, { key: 'Enter' });

    const mine = (await messages.findByText('Hi Ana')).closest('.sm-msg') as HTMLElement;

    expect(mine.classList.contains('sm-msg--first')).toBe(true);
    expect(mine.querySelector('time')).toBeTruthy();
  });

  it("stays an empty DM when they write in a group you're both in", async () => {
    const calls = liveBackend({ chatId: () => null });
    open();
    await screen.findByText(/say hi/);

    await pushToList({ type: 'CHAT_UPDATED', payload: { ...message(1, ana, 'Hi group', '2026-11-21T09:00:00'), chatId: 99 } });
    await settle();

    expect(calls.some(call => call.includes('/api/chats/99'))).toBe(false);
    expect(screen.getByText(/say hi/)).toBeTruthy();
  });

  it('keeps both when the same text is sent twice and one fails after the other is confirmed', async () => {
    const answers: ((response: Response) => void)[] = [];
    liveBackend({ history: [message(1, ana, 'Hello', '2026-11-21T09:00:00')], send: () => new Promise(resolve => { answers.push(resolve); }) });
    open();
    const messages = await log();
    const field = screen.getByRole('textbox', { name: 'Message Test Ana' });
    for (let i = 0; i < 2; i++) {
      fireEvent.change(field, { target: { value: 'haha' } });
      fireEvent.keyDown(field, { key: 'Enter' });
    }
    await waitFor(() => expect(answers).toHaveLength(2));
    const errors = vi.spyOn(console, 'error');

    answers[0](reply(message(2, me, 'haha', '2026-11-21T09:02:00'), 201));
    await settle();
    push({ type: 'MESSAGE_CREATED', payload: message(2, me, 'haha', '2026-11-21T09:02:00') });
    await settle();
    answers[1](reply({ code: 'CHAT_NOT_FOUND' }, 404));

    expect(await messages.findByRole('button', { name: 'Retry' })).toBeTruthy();
    expect(messages.getAllByText('haha')).toHaveLength(2);
    expect(errors.mock.calls.some(call => String(call[0]).includes('same key'))).toBe(false);
    errors.mockRestore();
  });

  it("gives each of two same texts its own key when the second one's event comes first", async () => {
    const answers: ((response: Response) => void)[] = [];
    liveBackend({ history: [message(1, ana, 'Hello', '2026-11-21T09:00:00')], send: () => new Promise(resolve => { answers.push(resolve); }) });
    open();
    const messages = await log();
    const field = screen.getByRole('textbox', { name: 'Message Test Ana' });
    for (let i = 0; i < 2; i++) {
      fireEvent.change(field, { target: { value: 'haha' } });
      fireEvent.keyDown(field, { key: 'Enter' });
    }
    await waitFor(() => expect(answers).toHaveLength(2));
    const errors = vi.spyOn(console, 'error');

    push({ type: 'MESSAGE_CREATED', payload: message(3, me, 'haha', '2026-11-21T09:02:01') });
    await settle();
    answers[0](reply(message(2, me, 'haha', '2026-11-21T09:02:00'), 201));
    await settle();
    answers[1](reply(message(3, me, 'haha', '2026-11-21T09:02:01'), 201));
    await settle();

    expect(messages.getAllByText('haha')).toHaveLength(2);
    expect(messages.queryByText('Sending…')).toBeNull();
    expect(errors.mock.calls.some(call => String(call[0]).includes('same key'))).toBe(false);
    errors.mockRestore();
  });

  it('waits for you to come back to the tab before marking a DM they wrote first as read', async () => {
    let dm: number | null = null;
    const calls = liveBackend({ chatId: () => dm, history: [message(1, ana, 'Hi there', '2026-11-21T09:00:00')] });
    open();
    await screen.findByText(/say hi/);
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');

    dm = CHAT;
    await pushToList({ type: 'CHAT_UPDATED', payload: message(1, ana, 'Hi there', '2026-11-21T09:00:00') });
    expect(await (await log()).findByText('Hi there')).toBeTruthy();
    await settle();
    expect(reads(calls)).toBe(0);

    visibility.mockReturnValue('visible');
    fireEvent(document, new Event('visibilitychange'));

    await waitFor(() => expect(reads(calls)).toBe(1));
    visibility.mockRestore();
  });

  it('puts the focus back in the message box after saving an edit', async () => {
    let answer: (response: Response) => void = () => {};
    liveBackend({ history: [message(1, ana, 'Hello', '2026-11-21T09:00:00'), message(2, me, 'Almost done', '2026-11-21T09:01:00')], edit: () => new Promise(resolve => { answer = resolve; }) });
    open();
    const messages = await log();
    fireEvent.click(within(messages.getByText('Almost done').closest('.sm-msg') as HTMLElement).getByRole('button', { name: 'Edit' }));
    const box = screen.getByRole('textbox', { name: 'Edit message' });
    box.focus();
    fireEvent.change(box, { target: { value: 'All done' } });
    fireEvent.keyDown(box, { key: 'Enter' });

    answer(reply(message(2, me, 'All done', '2026-11-21T09:01:00', '2026-11-21T09:03:00')));

    await waitFor(() => expect(screen.queryByRole('textbox', { name: 'Edit message' })).toBeNull());
    expect(document.activeElement).toBe(screen.getByRole('textbox', { name: 'Message Test Ana' }));
  });
});
