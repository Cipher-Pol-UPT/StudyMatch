import { cleanup, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { forgetConversations } from '../src/pages/chats/conversations';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { ChatMessage, StudyMatchProvider } from '../src/studymatch-ui';
import type { ChatContactData, ChatSummaryData, MessageData } from '../src/types/chat';

vi.mock('../src/api/chatSocket', () => ({ onChatSocketReconnect: () => () => undefined,
  isChatSocketConnected: () => false, onChatSocketConnection: () => () => undefined, sendTyping: vi.fn(),
  subscribeToChatEvents: () => () => undefined,
}));

configure({ asyncUtilTimeout: 5000 });
vi.setConfig({ testTimeout: 15000 });

const me: ChatContactData = { number: 9, name: 'Test Teacher', role: 'TEACHER' };
const ana: ChatContactData = { number: 50001, name: 'Test Ana', role: 'STUDENT' };
const rui: ChatContactData = { number: 50002, name: 'Test Rui', role: 'STUDENT' };
const CHAT = 7;
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
const message = (id: number, chatId: number, author: ChatContactData, text: string): MessageData =>
  ({ id, chatId, author, text, sentAt: '2026-11-20T10:00:00', editedAt: null, reply: false, replyTo: null });
const dm = (contact: ChatContactData): ChatSummaryData =>
  ({ chatId: CHAT, name: contact.name, contact, picture: false, pictureUpdatedAt: null, lastMessage: message(1, CHAT, contact, 'Hi there'), unread: 0, assignment: false });
const group = (last: MessageData): ChatSummaryData =>
  ({ chatId: 5, name: 'Test Study Group', contact: null, picture: false, pictureUpdatedAt: null, lastMessage: last, unread: 0, assignment: false });

interface State { blocked: ChatContactData[]; chats: ChatSummaryData[]; refuse?: string }

// a small backend that remembers who you blocked: blocking drops the DM from the list and unblocking brings it back
function backend(state: State) {
  const calls: string[] = [];
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    const method = init?.method ?? 'GET';
    calls.push(`${method} ${url.pathname}`);
    const blockedNow = (number: number) => state.blocked.some(person => person.number === number);
    if (url.pathname === '/api/blocks/') return reply(state.blocked);
    const blocking = url.pathname.match(/^\/api\/blocks\/(\d+)$/);
    if (blocking) {
      if (state.refuse) return reply({ code: state.refuse }, 400);
      const number = Number(blocking[1]);
      state.blocked = method === 'PUT' ? [...state.blocked, ana] : state.blocked.filter(person => person.number !== number);
      return new Response(null, { status: 204 });
    }
    if (url.pathname === `/api/chats/direct/${ana.number}`) return reply(blockedNow(ana.number) ? { chatId: null, contact: ana, blocked: true } : { chatId: CHAT, contact: ana, blocked: false });
    if (url.pathname === `/api/chats/${CHAT}/messages`) return reply({ hasMore: false, messages: [message(1, CHAT, ana, 'Hi there')], reactions: [] });
    if (url.pathname === `/api/chats/${CHAT}/read`) return new Response(null, { status: 204 });
    // a group chat with Ana and Rui
    if (url.pathname === '/api/chats/5/members') return reply([me, ana, rui]);
    if (url.pathname === '/api/chats/5/messages') return reply({ hasMore: false, messages: [message(50, 5, ana, 'From Ana'), message(51, 5, rui, 'Secret')], reactions: [] });
    if (url.pathname === '/api/chats/5/read') return new Response(null, { status: 204 });
    if (url.pathname === '/api/chats/') return reply(state.chats.filter(chat => !chat.contact || !blockedNow(chat.contact.number)));
    if (url.pathname === '/api/chats/unread-count') return reply(0);
    throw new Error(`Unexpected request: ${method} ${url.pathname}`);
  }));
  return calls;
}

function open(path: string) {
  render(<StudyMatchProvider><MemoryRouter initialEntries={[path]}><TeacherArea currentUser={{ ...me, institutionalEmail: 'teacher@school.pt', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}
const chatList = () => screen.findByRole('list', { name: 'Chats' });

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); forgetConversations(); });

describe('block a user', () => {
  it("blocks from the DM's menu after asking, and the DM leaves the list", async () => {
    const calls = backend({ blocked: [], chats: [dm(ana)] });
    open(`/chats/direct/${ana.number}`);
    await screen.findByText('Hi there', { selector: '.sm-msg__bubble' });

    fireEvent.click(screen.getByRole('button', { name: 'Chat options' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Block Test' }));
    const ask = await screen.findByRole('dialog', { name: 'Block Test Ana?' });
    expect(ask.textContent).toContain("they aren't told");
    fireEvent.click(within(ask).getByRole('button', { name: 'Block' }));

    expect(await screen.findByText('Test Ana is blocked.')).toBeTruthy();
    expect(calls).toContain(`PUT /api/blocks/${ana.number}`);
    expect(await screen.findByText('No chats yet')).toBeTruthy();
    expect(await screen.findByText('Pick a chat')).toBeTruthy();
  });

  it('shows what the backend says when it refuses', async () => {
    backend({ blocked: [], chats: [dm(ana)], refuse: 'CANNOT_BLOCK_YOURSELF' });
    open(`/chats/direct/${ana.number}`);
    await screen.findByText('Hi there', { selector: '.sm-msg__bubble' });

    fireEvent.click(screen.getByRole('button', { name: 'Chat options' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Block Test' }));
    fireEvent.click(within(await screen.findByRole('dialog', { name: 'Block Test Ana?' })).getByRole('button', { name: 'Block' }));

    expect(await screen.findByText("You can't block yourself.")).toBeTruthy();
  });

  it('opens a DM with someone you blocked without the message box, and unblocking brings it back', async () => {
    const calls = backend({ blocked: [ana], chats: [dm(ana)] });
    open(`/chats/direct/${ana.number}`);

    expect(await screen.findByText('You blocked Test.')).toBeTruthy();
    expect(screen.queryByRole('textbox', { name: 'Message Test Ana' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Chat options' })).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Unblock' }));

    expect(await screen.findByRole('textbox', { name: 'Message Test Ana' })).toBeTruthy();
    expect(await screen.findByText('Hi there', { selector: '.sm-msg__bubble' })).toBeTruthy();
    expect(calls).toContain(`DELETE /api/blocks/${ana.number}`);
    await waitFor(async () => expect(within(await chatList()).getByText('Test Ana')).toBeTruthy());
  });
});

describe('blocked users', () => {
  it('lists the people you blocked, and unblocking takes them off and brings their DM back', async () => {
    backend({ blocked: [ana], chats: [dm(ana)] });
    open('/chats/blocked');

    const people = await screen.findByRole('list', { name: 'Blocked users' });
    expect(within(people).getByText('Test Ana')).toBeTruthy();
    expect(within(people).getByText('Student · 50001')).toBeTruthy();

    fireEvent.click(within(people).getByRole('button', { name: 'Unblock Test Ana' }));

    expect(await screen.findByText('No one is blocked')).toBeTruthy();
    expect(await screen.findByText('Test Ana is unblocked.')).toBeTruthy();
    await waitFor(async () => expect(within(await chatList()).getByText('Test Ana')).toBeTruthy());
  });

  it('says when nobody is blocked, and is reached from the foot of the chat list', async () => {
    backend({ blocked: [], chats: [] });
    open('/chats');

    fireEvent.click(await screen.findByRole('link', { name: 'Blocked users' }));

    expect(await screen.findByText('No one is blocked')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Blocked users' }).getAttribute('aria-current')).toBe('page');
  });
});

describe('someone you blocked in a group chat', () => {
  it("hides their message in the chat list's preview", async () => {
    backend({ blocked: [rui], chats: [group(message(50, 5, rui, 'Secret'))] });
    open('/chats');

    const row = within(await chatList()).getByRole('link');
    await waitFor(() => expect(row.textContent).toContain('Message from someone you blocked'));
    expect(row.textContent).not.toContain('Secret');
  });

  it('hides their message in the open group chat until you click it', async () => {
    backend({ blocked: [rui], chats: [group(message(51, 5, rui, 'Secret'))] });
    open('/chats/group/5');

    const messages = within(await screen.findByRole('log', { name: 'Messages' }));
    expect(await messages.findByText('From Ana')).toBeTruthy();
    expect(messages.queryByText('Secret')).toBeNull();

    fireEvent.click(messages.getByRole('button', { name: 'Message from someone you blocked' }));

    expect(messages.getByText('Secret')).toBeTruthy();
  });

  it('hides their message behind a click', () => {
    const show = vi.fn();
    const { rerender } = render(<ChatMessage text="Secret" time="10:00" author="Test Rui" hidden={{ label: 'Message from someone you blocked', onShow: show }} />);

    expect(screen.queryByText('Secret')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Message from someone you blocked' }));
    expect(show).toHaveBeenCalled();

    rerender(<ChatMessage text="Secret" time="10:00" author="Test Rui" />);
    expect(screen.getByText('Secret')).toBeTruthy();
  });
});
