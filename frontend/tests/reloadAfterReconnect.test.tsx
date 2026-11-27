import { act, cleanup, configure, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { forgetConversations } from '../src/pages/chats/conversations';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { ChatContactData, ChatSummaryData, MessageData, MessagePageData } from '../src/types/chat';

// the live connection, faked: the test says when it comes back
const live = vi.hoisted(() => ({ reconnect: new Set<() => void>() }));
vi.mock('../src/api/chatSocket', () => ({
  onChatSocketReconnect: (listener: () => void) => { live.reconnect.add(listener); return () => live.reconnect.delete(listener); },
  // the online dots aren't what's tested here
  isChatSocketConnected: () => false, onChatSocketConnection: () => () => undefined, sendTyping: vi.fn(),
  subscribeToChatEvents: () => () => undefined,
}));

configure({ asyncUtilTimeout: 5000 });
vi.setConfig({ testTimeout: 15000 });

const me: ChatContactData = { number: 9, name: 'Test Teacher', role: 'TEACHER' };
const ana: ChatContactData = { number: 50001, name: 'Test Ana', role: 'STUDENT' };
const CHAT = 7;
const reply = (body: unknown) => new Response(JSON.stringify(body), { status: 200 });
const message = (id: number, text: string, editedAt: string | null = null): MessageData =>
  ({ id, chatId: CHAT, author: ana, text, sentAt: '2026-11-20T10:00:00', editedAt, reply: false, replyTo: null });
const row = (last: MessageData, unread: number): ChatSummaryData =>
  ({ chatId: CHAT, name: ana.name, contact: ana, picture: false, pictureUpdatedAt: null, lastMessage: last, unread, assignment: false });

// the first page when the chat opens; `after` answers each ?after= asked after the reconnect; `lists` each GET /chats/
function backend(first: MessageData[], after: (from: number) => MessagePageData, lists: ChatSummaryData[][]) {
  const calls: string[] = [];
  let listCall = 0;
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async input => {
    const url = new URL(String(input), 'https://test.invalid');
    calls.push(`${url.pathname}${url.search}`);
    if (url.pathname === `/api/chats/direct/${ana.number}`) return reply({ chatId: CHAT, contact: ana, blocked: false });
    if (url.pathname === `/api/chats/${CHAT}/messages`) {
      const from = url.searchParams.get('after');
      return reply(from === null ? { messages: first, reactions: [], hasMore: false } : after(Number(from)));
    }
    if (url.pathname === `/api/chats/${CHAT}/read`) return new Response(null, { status: 204 });
    if (url.pathname === '/api/chats/') return reply(lists[Math.min(listCall++, lists.length - 1)]);
    if (url.pathname === '/api/chats/unread-count') return reply(0);
    throw new Error(`Unexpected request: ${url.pathname}`);
  }));
  return calls;
}

function open() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={[`/chats/direct/${ana.number}`]}><TeacherArea currentUser={{ ...me, institutionalEmail: 'teacher@school.pt', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}
const reconnect = () => act(() => live.reconnect.forEach(listener => listener()));
const shown = () => [...document.querySelectorAll('.sm-msg__bubble')].map(bubble => bubble.firstChild?.textContent);

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); forgetConversations(); live.reconnect.clear(); });

describe('after the connection comes back', () => {
  it('loads a message, an edit and a delete that happened while it was down, and the chat list again', async () => {
    const calls = backend(
      [message(1, 'Hi'), message(2, 'Typo hree'), message(3, 'Wrong chat')],
      // 2 was edited, 3 deleted, and 4 is new
      () => ({ messages: [message(1, 'Hi'), message(2, 'Typo here', '2026-11-20T10:05:00'), message(4, 'Are you there?')], reactions: [], hasMore: false, hasNewer: false }),
      [[row(message(3, 'Wrong chat'), 0)], [row(message(4, 'Are you there?'), 1)]],
    );
    open();
    await waitFor(() => expect(shown()).toEqual(['Hi', 'Typo hree', 'Wrong chat']));

    reconnect();

    await waitFor(() => expect(shown()).toEqual(['Hi', 'Typo here', 'Are you there?']));
    expect(screen.getByText('(edited)')).toBeTruthy();
    expect(calls).toContain(`/api/chats/${CHAT}/messages?after=0`);
    const list = await screen.findByRole('list', { name: 'Chats' });
    await waitFor(() => expect(within(list).getByRole('link').textContent).toContain('Are you there?'));
    // it's open, so what came is read
    await waitFor(() => expect(calls.filter(call => call === `/api/chats/${CHAT}/read`)).toHaveLength(2));
  });

  it('asks 50 at a time until the latest', async () => {
    const calls = backend(
      [message(10, 'Before')],
      from => from < 20
        ? { messages: [message(10, 'Before'), message(20, 'First page')], reactions: [], hasMore: false, hasNewer: true }
        : { messages: [message(30, 'Second page')], reactions: [], hasMore: false, hasNewer: false },
      [[row(message(10, 'Before'), 0)]],
    );
    open();
    await waitFor(() => expect(shown()).toEqual(['Before']));

    reconnect();

    await waitFor(() => expect(shown()).toEqual(['Before', 'First page', 'Second page']));
    expect(calls.filter(call => call.includes('?after='))).toEqual([`/api/chats/${CHAT}/messages?after=9`, `/api/chats/${CHAT}/messages?after=20`]);
  });

  it('shows the first message of a chat that was empty when it dropped', async () => {
    // the first page is read when it's asked for, so what's pushed after opening is there for the reconnect
    const first: MessageData[] = [];
    const calls = backend(first, () => { throw new Error('not asked'); }, [[], [row(message(1, 'First one'), 1)]]);
    open();
    await waitFor(() => expect(calls).toContain(`/api/chats/${CHAT}/messages`));
    expect(shown()).toEqual([]);

    first.push(message(1, 'First one'));
    reconnect();

    await waitFor(() => expect(shown()).toEqual(['First one']));
    expect(calls.filter(call => call.includes('?after='))).toEqual([]);
    expect(calls.filter(call => call === `/api/chats/${CHAT}/messages`)).toHaveLength(2);
    await waitFor(() => expect(calls).toContain(`/api/chats/${CHAT}/read`));
  });

  it('asks for nothing on the first connection', async () => {
    const calls = backend([message(1, 'Hi')], () => { throw new Error('not asked'); }, [[row(message(1, 'Hi'), 0)]]);
    open();
    await waitFor(() => expect(shown()).toEqual(['Hi']));

    expect(calls.filter(call => call.includes('?after='))).toEqual([]);
    expect(calls.filter(call => call === '/api/chats/')).toHaveLength(1);
  });
});
