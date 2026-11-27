import { act, cleanup, configure, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { forgetConversations } from '../src/pages/chats/conversations';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { ChatContactData, ChatSummaryData, MessageData } from '../src/types/chat';

// the live connection, faked: every listener of a destination gets what the test pushes
const live = vi.hoisted(() => ({ listeners: new Map<string, Set<(event: unknown) => void>>() }));
vi.mock('../src/api/chatSocket', () => ({
  // the connection is down, so nobody shows as online
  isChatSocketConnected: () => false, onChatSocketConnection: () => () => undefined, sendTyping: vi.fn(),
  connectChatSocket: vi.fn(),
  disconnectChatSocket: vi.fn(async () => {}),
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
const ana: ChatContactData = { number: 50001, name: 'Test Ana', role: 'STUDENT' };
const rui: ChatContactData = { number: 50002, name: 'Test Rui', role: 'STUDENT' };
const reply = (body: unknown) => new Response(JSON.stringify(body), { status: 200 });
const today = (hour: number) => { const d = new Date(); d.setHours(hour, 0, 0, 0); return d.toISOString().slice(0, 19); };

const message = (id: number, chatId: number, author: ChatContactData, text: string, sentAt = today(9)): MessageData =>
  ({ id, chatId, author, text, sentAt, editedAt: null, reply: false, replyTo: null });
const dm = (chatId: number, contact: ChatContactData, last: MessageData | null, unread = 0): ChatSummaryData =>
  ({ chatId, name: contact.name, contact, picture: false, pictureUpdatedAt: null, lastMessage: last, unread, assignment: false });
const group = (chatId: number, name: string, last: MessageData | null, picture = false): ChatSummaryData =>
  ({ chatId, name, contact: null, picture, pictureUpdatedAt: picture ? '2026-11-20T10:00:00' : null, lastMessage: last, unread: 0, assignment: false });
const daysAgo = (days: number) => { const d = new Date(); d.setDate(d.getDate() - days); d.setHours(10, 0, 0, 0); return d.toISOString().slice(0, 19); };

// the chat list and the unread count; `lists` answers each GET /chats/ in turn
function backend(lists: ChatSummaryData[][], unread: number[] = [0]) {
  const calls: string[] = [];
  let listCall = 0;
  let countCall = 0;
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async input => {
    const url = new URL(String(input), 'https://test.invalid');
    calls.push(url.pathname);
    if (url.pathname === '/api/chats/') return reply(lists[Math.min(listCall++, lists.length - 1)]);
    if (url.pathname === '/api/chats/unread-count') return reply(unread[Math.min(countCall++, unread.length - 1)]);
    throw new Error(`Unexpected request: ${url.pathname}`);
  }));
  return calls;
}

function open() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={['/chats']}><TeacherArea currentUser={{ ...me, institutionalEmail: 'teacher@school.pt', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

const list = () => screen.findByRole('list', { name: 'Chats' });
const rowNames = (box: HTMLElement) => within(box).getAllByRole('link').map(link => link.querySelector('.sm-chat__row-name')?.textContent);
// what the backend pushes to the chat list, once the page listens to it
async function push(event: unknown) {
  await waitFor(() => expect(live.listeners.get('/user/queue/chats')?.size).toBeGreaterThan(0));
  act(() => live.listeners.get('/user/queue/chats')?.forEach(listener => listener(event)));
}

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); forgetConversations(); live.listeners.clear(); });

describe('view chats', () => {
  it('lists the chats newest first, with the latest message, when, and how many are unread', async () => {
    backend([[dm(2, rui, message(20, 2, rui, 'See you tomorrow', today(11)), 3), dm(1, ana, message(10, 1, me, 'On my way', today(8)))]]);
    open();

    const box = await list();

    expect(rowNames(box)).toEqual(['Test Rui', 'Test Ana']);
    const rows = within(box).getAllByRole('link');
    expect(rows[0].textContent).toContain('See you tomorrow');
    expect(rows[0].textContent).toContain('11:00');
    expect(within(rows[0]).getByText('3 unread messages')).toBeTruthy();
    expect(rows[1].textContent).toContain('You: On my way');
    expect(rows[1].getAttribute('href')).toBe('/chats/direct/50001');
  });

  it('says there are no chats yet, with a way to start one', async () => {
    backend([[]]);
    open();

    expect(await screen.findByText('No chats yet')).toBeTruthy();
    expect(screen.getAllByRole('button', { name: 'New message' }).length).toBe(2);
  });

  it('moves a chat to the top with its badge up when a message arrives, without reloading', async () => {
    const calls = backend([[dm(1, ana, message(10, 1, ana, 'Hi')), dm(2, rui, message(20, 2, rui, 'Earlier', today(7)))]]);
    open();
    await list();

    await push({ type: 'CHAT_UPDATED', payload: message(21, 2, rui, 'Are you there?', today(12)) });

    const box = await list();
    expect(rowNames(box)).toEqual(['Test Rui', 'Test Ana']);
    expect(within(box).getAllByRole('link')[0].textContent).toContain('Are you there?');
    expect(within(box).getByText('1 unread message')).toBeTruthy();
    expect(calls.filter(call => call === '/api/chats/')).toHaveLength(1);
  });

  it("doesn't count your own message as unread", async () => {
    backend([[dm(1, ana, message(10, 1, ana, 'Hi'))]]);
    open();
    await list();

    await push({ type: 'CHAT_UPDATED', payload: message(11, 1, me, 'Hello', today(12)) });

    const box = await list();
    expect(within(box).getByRole('link').textContent).toContain('You: Hello');
    expect(within(box).queryByText(/unread/)).toBeNull();
  });

  it('clears the badge when the chat is read in another tab, and drops a chat you were removed from', async () => {
    backend([[dm(1, ana, message(10, 1, ana, 'Hi'), 2), dm(2, rui, message(20, 2, rui, 'Yo', today(7)))]]);
    open();
    const box = await list();
    expect(within(box).getByText('2 unread messages')).toBeTruthy();

    await push({ type: 'CHAT_READ', payload: 1 });
    await push({ type: 'CHAT_REMOVED', payload: 2 });

    await waitFor(() => expect(rowNames(box)).toEqual(['Test Ana']));
    expect(within(box).queryByText(/unread/)).toBeNull();
  });

  it('asks for the list again when a chat it doesn\'t know gets a message', async () => {
    const calls = backend([[dm(1, ana, message(10, 1, ana, 'Hi'))], [dm(3, rui, message(30, 3, rui, 'New here', today(12)), 1), dm(1, ana, message(10, 1, ana, 'Hi'))]]);
    open();
    await list();

    await push({ type: 'CHAT_UPDATED', payload: message(30, 3, rui, 'New here', today(12)) });

    await waitFor(() => expect(rowNames(screen.getByRole('list', { name: 'Chats' }))).toEqual(['Test Rui', 'Test Ana']));
    expect(calls.filter(call => call === '/api/chats/')).toHaveLength(2);
  });

  it('shows the unread count on Chats in the side menu, and asks again when it changes', async () => {
    backend([[dm(1, ana, message(10, 1, ana, 'Hi'), 4)]], [4, 5]);
    open();
    await list();

    const chatsItem = () => screen.getAllByRole('link', { name: /Chats/ }).find(link => link.getAttribute('href') === '/chats')!;
    await waitFor(() => expect(chatsItem().textContent).toContain('4'));
    await push({ type: 'CHAT_UPDATED', payload: message(11, 1, ana, 'Again', today(12)) });

    await waitFor(() => expect(chatsItem().textContent).toContain('5'));
  });

  it('says when older messages were sent, and how a group chat reads', async () => {
    backend([[
      dm(1, ana, message(10, 1, ana, 'From yesterday', daysAgo(1))),
      group(5, 'Test Study Group', message(50, 5, rui, 'Bring the slides', daysAgo(1)), true),
      group(6, 'Test Empty Group', null),
      dm(2, rui, message(20, 2, rui, 'Long ago', '2026-10-05T10:00:00')),
    ]]);
    open();

    const rows = within(await list()).getAllByRole('link');

    expect(rows[0].textContent).toContain('Yesterday');
    expect(rows[1].textContent).toContain('Test: Bring the slides');
    expect(rows[1].getAttribute('href')).toBe('/chats/group/5');
    expect(rows[1].querySelector('img')?.getAttribute('src')).toBe('/api/chats/5/picture?v=2026-11-20T10:00:00');
    expect(rows[2].textContent).toContain('No messages yet');
    expect(rows[3].textContent).toMatch(/5 Oct|Oct 5/);
  });

  it('adds a chat you were added to, renames a group chat and shows an edited last message', async () => {
    backend([[group(5, 'Test Old Name', message(50, 5, rui, 'Typo hree'))]]);
    open();
    await list();

    await push({ type: 'CHAT_ADDED', payload: dm(7, ana, message(70, 7, ana, 'Welcome')) });
    await push({ type: 'CHAT_EDITED', payload: { chatId: 5, name: 'Test New Name', picture: false, pictureUpdatedAt: null } });
    await push({ type: 'MESSAGE_EDITED', payload: message(50, 5, rui, 'Typo here') });
    await push({ type: 'TYPING', payload: { chatId: 5 } });

    const box = await list();
    await waitFor(() => expect(rowNames(box)).toEqual(['Test Ana', 'Test New Name']));
    expect(within(box).getAllByRole('link')[1].textContent).toContain('Test: Typo here');
  });

  it('asks for the list again when a message in it is deleted', async () => {
    const calls = backend([[dm(1, ana, message(10, 1, ana, 'Oops'), 1)], [dm(1, ana, message(9, 1, ana, 'Before'))]]);
    open();
    await list();

    await push({ type: 'MESSAGE_DELETED', payload: { chatId: 1, messageId: 10 } });
    await push({ type: 'MESSAGE_DELETED', payload: { chatId: 99, messageId: 1 } });

    await waitFor(() => expect(within(screen.getByRole('list', { name: 'Chats' })).getByRole('link').textContent).toContain('Before'));
    expect(calls.filter(call => call === '/api/chats/')).toHaveLength(2);
  });

  it("shows the backend's error when the list can't load", async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async input => {
      if (String(input).endsWith('/chats/')) return new Response(JSON.stringify({ code: 'ACCESS_DENIED' }), { status: 403 });
      // nobody blocked, and the side menu's count
      return new Response(String(input).endsWith('/blocks/') ? '[]' : '0', { status: 200 });
    }));
    open();

    expect((await screen.findByRole('alert')).textContent).toBe(i18n.t('errors.ACCESS_DENIED'));
  });

  it("asks the backend for a group chat's row when someone else writes in it, so a blocked person never counts", async () => {
    // Rui is blocked: the backend doesn't count his message, so the row comes back with nothing unread
    const calls = backend([[group(5, 'Test Study Group', message(50, 5, ana, 'Hi all'))], [group(5, 'Test Study Group', message(51, 5, rui, 'From Rui', today(12)))]]);
    open();
    await list();

    await push({ type: 'CHAT_UPDATED', payload: message(51, 5, rui, 'From Rui', today(12)) });

    await waitFor(() => expect(within(screen.getByRole('list', { name: 'Chats' })).getByRole('link').textContent).toContain('From Rui'));
    expect(within(screen.getByRole('list', { name: 'Chats' })).queryByText(/unread/)).toBeNull();
    expect(calls.filter(call => call === '/api/chats/')).toHaveLength(2);
  });

  it('shows what a message with only files has: a photo, the file name, or how many', async () => {
    const withFiles = (id: number, chatId: number, author: ChatContactData, files: { name: string; contentType: string }[]) =>
      ({ ...message(id, chatId, author, ''), attachments: files.map((file, index) => ({ id: index + 1, size: 1000, ...file })) });
    backend([[
      dm(1, ana, withFiles(10, 1, me, [{ name: 'notes.pdf', contentType: 'application/pdf' }])),
      dm(2, rui, withFiles(20, 2, rui, [{ name: 'board.jpg', contentType: 'image/jpeg' }])),
      group(5, 'Test Study Group', withFiles(50, 5, ana, [{ name: 'a.pdf', contentType: 'application/pdf' }, { name: 'b.png', contentType: 'image/png' }])),
    ]]);
    open();

    const rows = within(await list()).getAllByRole('link');

    expect(rows[0].textContent).toContain('You: notes.pdf');
    expect(rows[1].textContent).toContain('Photo');
    expect(rows[2].textContent).toContain('Test: 2 files');
  });
});
