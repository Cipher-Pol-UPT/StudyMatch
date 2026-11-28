import { cleanup, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { forgetConversations } from '../src/pages/chats/conversations';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { ChatContactData, ChatSummaryData } from '../src/types/chat';

vi.mock('../src/api/chatSocket', () => ({ onChatSocketReconnect: () => () => undefined,
  isChatSocketConnected: () => false, onChatSocketConnection: () => () => undefined, sendTyping: vi.fn(),
  subscribeToChatEvents: () => () => undefined,
}));

configure({ asyncUtilTimeout: 5000 });
vi.setConfig({ testTimeout: 15000 });

const me: ChatContactData = { number: 9, name: 'Test Teacher', role: 'TEACHER' };
const ana: ChatContactData = { number: 50001, name: 'Test Ana', role: 'STUDENT' };
const rui: ChatContactData = { number: 50002, name: 'Test Rui', role: 'STUDENT' };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
const group = (assignment = false): ChatSummaryData =>
  ({ chatId: 5, name: 'Test Study Group', contact: null, picture: false, pictureUpdatedAt: null, lastMessage: null, unread: 0, assignment });
const other: ChatSummaryData = { ...group(), chatId: 6, name: 'Test Other Group' };

interface Call { method: string; path: string }

// two group chats; `members` is who's in chat 5, the owner first. `leave` answers leaving it
function backend({ assignment = false, members = [ana, me, rui], leave }: { assignment?: boolean; members?: ChatContactData[]; leave?: () => Response } = {}) {
  const calls: Call[] = [];
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    const call = { method: init?.method ?? 'GET', path: url.pathname };
    calls.push(call);
    if (url.pathname === '/api/chats/5/leave') return leave ? leave() : new Response(null, { status: 204 });
    if (url.pathname === '/api/chats/5/members') return json(members);
    if (url.pathname === '/api/chats/5/messages') return json({ messages: [], reactions: [], hasMore: false, hasNewer: false });
    if (url.pathname === '/api/chats/5/read') return new Response(null, { status: 204 });
    if (url.pathname === `/api/chats/direct/${ana.number}`) return json({ chatId: 7, contact: ana, blocked: false });
    if (url.pathname === '/api/chats/7/messages') return json({ messages: [], reactions: [], hasMore: false, hasNewer: false });
    if (url.pathname === '/api/chats/7/read') return new Response(null, { status: 204 });
    if (url.pathname === '/api/chats/') return json([group(assignment), other]);
    if (url.pathname === '/api/chats/unread-count') return json(0);
    if (url.pathname === '/api/blocks/') return json([]);
    throw new Error(`Unexpected request: ${call.method} ${url.pathname}`);
  }));
  return calls;
}

function open(path = '/chats/group/5') {
  render(<StudyMatchProvider><MemoryRouter initialEntries={[path]}><TeacherArea currentUser={{ ...me, institutionalEmail: 'teacher@school.pt', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

async function askToLeave() {
  await screen.findByText('3 members');
  fireEvent.click(await screen.findByRole('button', { name: 'Chat options' }));
  fireEvent.click(await screen.findByRole('menuitem', { name: 'Leave' }));
  return within(await screen.findByRole('dialog', { name: 'Leave Test Study Group?' }));
}
const listed = () => screen.queryAllByRole('link').map(link => link.textContent).filter(text => text?.includes('Group'));

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); forgetConversations(); });

describe('leave a group chat', () => {
  it('asks first, then leaves: the chat goes from the list and the list opens', async () => {
    const calls = backend();
    open();
    const dialog = await askToLeave();

    expect(dialog.getByText("You won't see its messages anymore.")).toBeTruthy();
    fireEvent.click(dialog.getByRole('button', { name: 'Leave' }));

    expect(await screen.findByText('You left Test Study Group.')).toBeTruthy();
    expect(calls.filter(call => call.method === 'POST').map(call => call.path)).toEqual(['/api/chats/5/leave']);
    await waitFor(() => expect(listed()).toEqual([expect.stringContaining('Test Other Group')]));
    expect(screen.queryByText('3 members')).toBeNull();
  });

  it('tells the owner who becomes the owner', async () => {
    backend({ members: [me, ana, rui] });
    open();
    const dialog = await askToLeave();

    expect(dialog.getByText("You won't see its messages anymore. You own it, so Test Ana becomes the owner.")).toBeTruthy();
  });

  it('changes nothing when Cancel is clicked', async () => {
    const calls = backend();
    open();
    const dialog = await askToLeave();

    fireEvent.click(dialog.getByRole('button', { name: 'Cancel' }));

    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Leave Test Study Group?' })).toBeNull());
    expect(calls.filter(call => call.method === 'POST')).toEqual([]);
    expect(screen.getByText('3 members')).toBeTruthy();
  });

  it('shows a toast when the backend refuses, and stays in the chat', async () => {
    backend({ leave: () => json({ code: 'CHAT_MEMBERS_FIXED' }, 409) });
    open();
    const dialog = await askToLeave();

    fireEvent.click(dialog.getByRole('button', { name: 'Leave' }));

    expect(await screen.findByText("The members of this chat can't be changed.")).toBeTruthy();
    expect(screen.getByText('3 members')).toBeTruthy();
  });

  it("isn't offered on an assignment group's chat or in a DM", async () => {
    backend({ assignment: true });
    open();
    await screen.findByText('3 members');
    // the menu may have other entries, like Members
    const options = screen.queryByRole('button', { name: 'Chat options' });
    if (options) fireEvent.click(options);
    expect(screen.queryByRole('menuitem', { name: 'Leave' })).toBeNull();
    cleanup();

    backend();
    open(`/chats/direct/${ana.number}`);
    fireEvent.click(await screen.findByRole('button', { name: 'Chat options' }));
    expect(await screen.findByRole('menuitem', { name: 'Block Test' })).toBeTruthy();
    expect(screen.queryByRole('menuitem', { name: 'Leave' })).toBeNull();
  });
});
