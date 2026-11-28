import { act, cleanup, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { forgetConversations } from '../src/pages/chats/conversations';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { ChatContactData, ChatEvent, ChatSummaryData } from '../src/types/chat';

// every listener of a destination gets what the test pushes
const live = vi.hoisted(() => ({ listeners: new Map<string, Set<(event: unknown) => void>>() }));
vi.mock('../src/api/chatSocket', () => ({ onChatSocketReconnect: () => () => undefined,
  isChatSocketConnected: () => false, onChatSocketConnection: () => () => undefined, sendTyping: vi.fn(),
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
const eva: ChatContactData = { number: 50003, name: 'Test Eva', role: 'STUDENT' };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
const group = (assignment = false): ChatSummaryData =>
  ({ chatId: 5, name: 'Test Study Group', contact: null, picture: false, pictureUpdatedAt: null, lastMessage: null, unread: 0, assignment });

interface Call { method: string; path: string; body?: unknown }

// a group chat; `members` answers each GET of its members in turn, the owner first. `remove` answers the DELETE
function backend({ assignment = false, members = [[me, ana, rui, eva]], remove }: { assignment?: boolean; members?: ChatContactData[][]; remove?: (number: number) => Response } = {}) {
  const calls: Call[] = [];
  let membersCall = 0;
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    const call = { method: init?.method ?? 'GET', path: url.pathname };
    calls.push(call);
    const removed = /^\/api\/chats\/5\/members\/(\d+)$/.exec(url.pathname);
    if (removed && call.method === 'DELETE') {
      const number = Number(removed[1]);
      return remove ? remove(number) : json(members[members.length - 1].filter(person => person.number !== number));
    }
    if (url.pathname === '/api/chats/5/members') return json(members[Math.min(membersCall++, members.length - 1)]);
    if (url.pathname === '/api/chats/5/messages') return json({ messages: [], reactions: [], hasMore: false, hasNewer: false });
    if (url.pathname === '/api/chats/5/read') return new Response(null, { status: 204 });
    if (url.pathname === '/api/chats/') return json([group(assignment)]);
    if (url.pathname === '/api/chats/unread-count') return json(0);
    if (url.pathname === '/api/blocks/') return json([]);
    throw new Error(`Unexpected request: ${call.method} ${url.pathname}`);
  }));
  return calls;
}

function open() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={['/chats/group/5']}><TeacherArea currentUser={{ ...me, institutionalEmail: 'teacher@school.pt', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

async function openMembers() {
  fireEvent.click(await screen.findByRole('button', { name: 'Chat options' }));
  fireEvent.click(await screen.findByRole('menuitem', { name: 'Members' }));
  const dialog = within(await screen.findByRole('dialog', { name: 'People in Test Study Group' }));
  await dialog.findByRole('list');
  return dialog;
}
const names = (dialog: ReturnType<typeof within>) => dialog.queryAllByText(/^Test /, { selector: '.sm-person__name' }).map(name => name.textContent);
const removeButtons = (dialog: ReturnType<typeof within>) => dialog.queryAllByRole('button', { name: /^Remove / }).map(button => button.getAttribute('aria-label'));

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); forgetConversations(); live.listeners.clear(); });

describe('remove a member from a group chat', () => {
  it('lists the members with the owner first, and the owner removes someone after confirming', async () => {
    const calls = backend();
    open();
    expect(await screen.findByText('4 members')).toBeTruthy();
    const dialog = await openMembers();

    expect(names(dialog)).toEqual(['Test Teacher', 'Test Ana', 'Test Rui', 'Test Eva']);
    expect(dialog.getByText('Owner')).toBeTruthy();
    expect(removeButtons(dialog)).toEqual(['Remove Test Ana', 'Remove Test Rui', 'Remove Test Eva']);
    fireEvent.click(dialog.getByRole('button', { name: 'Remove Test Rui' }));
    const confirm = within(await screen.findByRole('dialog', { name: 'Remove Test Rui?' }));
    expect(confirm.getByText("They leave Test Study Group straight away and won't see its messages anymore.")).toBeTruthy();
    fireEvent.click(confirm.getByRole('button', { name: 'Remove' }));

    expect(await screen.findByText('Test Rui was removed from Test Study Group.')).toBeTruthy();
    expect(calls.filter(call => call.method === 'DELETE').map(call => call.path)).toEqual(['/api/chats/5/members/50002']);
    await waitFor(() => expect(names(dialog)).toEqual(['Test Teacher', 'Test Ana', 'Test Eva']));
    // the head and the dialog both count them again
    expect(screen.getAllByText('3 members')).toHaveLength(2);
  });

  it('shows Remove only to the owner', async () => {
    backend({ members: [[ana, me, rui]] });
    open();
    const dialog = await openMembers();

    expect(names(dialog)).toEqual(['Test Ana', 'Test Teacher', 'Test Rui']);
    expect(within(dialog.getByText('Test Ana').closest('li')!).getByText('Owner')).toBeTruthy();
    expect(within(dialog.getByText('Test Teacher').closest('li')!).getByText('You')).toBeTruthy();
    expect(removeButtons(dialog)).toEqual([]);
  });

  it("shows the members of an assignment group's chat without Remove, they follow the group", async () => {
    backend({ assignment: true });
    open();
    const dialog = await openMembers();

    expect(names(dialog)).toHaveLength(4);
    expect(removeButtons(dialog)).toEqual([]);
  });

  it('asks who is in it each time it opens, since nothing is pushed when someone else changes them', async () => {
    const calls = backend({ members: [[me, ana, rui], [me, ana, rui, eva]] });
    open();
    await screen.findByText('3 members');

    const dialog = await openMembers();

    expect(names(dialog)).toEqual(['Test Teacher', 'Test Ana', 'Test Rui', 'Test Eva']);
    expect(calls.filter(call => call.method === 'GET' && call.path === '/api/chats/5/members')).toHaveLength(2);
  });

  it('shows a toast when the backend refuses', async () => {
    backend({ remove: () => json({ code: 'NOT_CHAT_OWNER' }, 403) });
    open();
    const dialog = await openMembers();
    fireEvent.click(dialog.getByRole('button', { name: 'Remove Test Ana' }));
    fireEvent.click(within(await screen.findByRole('dialog', { name: 'Remove Test Ana?' })).getByRole('button', { name: 'Remove' }));

    expect(await screen.findByText('Only the owner of this group chat can do that.')).toBeTruthy();
    expect(names(dialog)).toContain('Test Ana');
  });

  it('tells the removed member while they have it open, and takes it off their list', async () => {
    backend({ members: [[ana, me, rui]] });
    open();
    await screen.findByText('3 members');
    expect(await screen.findByRole('link', { name: /Test Study Group/ })).toBeTruthy();

    act(() => live.listeners.get('/user/queue/chats')?.forEach(listener => listener({ type: 'CHAT_REMOVED', payload: 5 } satisfies ChatEvent)));

    expect(await screen.findByText("You're no longer in Test Study Group")).toBeTruthy();
    expect(screen.getByText("Its messages aren't available to you anymore.")).toBeTruthy();
    expect(screen.queryByRole('link', { name: /Test Study Group/ })).toBeNull();
    expect(screen.queryByRole('textbox', { name: /Message/ })).toBeNull();
  });
});
