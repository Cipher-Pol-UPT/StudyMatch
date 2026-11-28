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
const eva: ChatContactData = { number: 50003, name: 'Test Eva', role: 'STUDENT' };
const leo: ChatContactData = { number: 50004, name: 'Test Leo', role: 'STUDENT' };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
const group = (assignment = false): ChatSummaryData =>
  ({ chatId: 5, name: 'Test Study Group', contact: null, picture: false, pictureUpdatedAt: null, lastMessage: null, unread: 0, assignment });
const filler = (count: number) => Array.from({ length: count }, (_, index): ChatContactData => ({ number: 61000 + index, name: `Test Filler ${index}`, role: 'STUDENT' }));

interface Call { method: string; path: string; body?: unknown }

// a group chat with you, Ana and Rui; `members` answers each GET of its members in turn, `add` the POST
function backend({ assignment = false, members = [[me, ana, rui]], add }: { assignment?: boolean; members?: ChatContactData[][]; add?: (numbers: number[]) => Response } = {}) {
  const calls: Call[] = [];
  let membersCall = 0;
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    const call = { method: init?.method ?? 'GET', path: url.pathname, body: init?.body ? JSON.parse(String(init.body)) : undefined };
    calls.push(call);
    if (url.pathname === '/api/chats/5/members' && call.method === 'POST') {
      const numbers = (call.body as { memberNumbers: number[] }).memberNumbers;
      return add ? add(numbers) : json([...members[members.length - 1], ...[eva, leo].filter(person => numbers.includes(person.number))]);
    }
    if (url.pathname === '/api/chats/5/members') return json(members[Math.min(membersCall++, members.length - 1)]);
    if (url.pathname === '/api/users/chat-contacts') return json({ content: [ana, rui, eva, leo], totalElements: 4, totalPages: 1, number: 0, size: 25 });
    if (url.pathname === '/api/chats/5/messages') return json({ messages: [], reactions: [], hasMore: false, hasNewer: false });
    if (url.pathname === '/api/chats/5/read') return new Response(null, { status: 204 });
    if (url.pathname === '/api/chats/') return json([group(assignment)]);
    if (url.pathname === '/api/chats/unread-count') return json(0);
    throw new Error(`Unexpected request: ${call.method} ${url.pathname}`);
  }));
  return calls;
}

function open() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={['/chats/group/5']}><TeacherArea currentUser={{ ...me, institutionalEmail: 'teacher@school.pt', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

async function startAdding() {
  fireEvent.click(await screen.findByRole('button', { name: 'Chat options' }));
  fireEvent.click(await screen.findByRole('menuitem', { name: 'Add members' }));
  return within(await screen.findByRole('dialog', { name: 'Add members to Test Study Group' }));
}
const names = (dialog: ReturnType<typeof within>) => dialog.queryAllByText(/^Test /, { selector: '.sm-person__name' }).map(name => name.textContent);
const row = async (dialog: ReturnType<typeof within>, name: string) => (await dialog.findByText(name, { selector: '.sm-person__name' })).closest('button') as HTMLButtonElement;

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); forgetConversations(); });

describe('add members to a group chat', () => {
  it('offers only people who are not in it, adds the ones picked, and the head counts them', async () => {
    const calls = backend();
    open();
    expect(await screen.findByText('3 members')).toBeTruthy();
    const dialog = await startAdding();

    await waitFor(() => expect(names(dialog)).toEqual(['Test Eva', 'Test Leo']));
    expect(dialog.getByText('17 places left')).toBeTruthy();
    fireEvent.click(await row(dialog, 'Test Eva'));
    fireEvent.click(await row(dialog, 'Test Leo'));
    fireEvent.click(dialog.getByRole('button', { name: 'Add 2 people' }));

    expect(await screen.findByText('2 people were added.')).toBeTruthy();
    expect(calls.find(call => call.method === 'POST')?.body).toEqual({ memberNumbers: [eva.number, leo.number] });
    expect(screen.getByText('5 members')).toBeTruthy();
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Add members to Test Study Group' })).toBeNull());
  });

  it('asks who is in it each time it opens, since nothing is pushed when someone else adds people', async () => {
    const calls = backend({ members: [[me, ana, rui], [me, ana, rui, eva]] });
    open();
    await screen.findByText('3 members');

    const dialog = await startAdding();

    await waitFor(() => expect(names(dialog)).toEqual(['Test Leo']));
    expect(calls.filter(call => call.method === 'GET' && call.path === '/api/chats/5/members')).toHaveLength(2);
  });

  it('stops at 20 members', async () => {
    backend({ members: [[me, ana, rui, ...filler(16)]] });
    open();
    const dialog = await startAdding();

    fireEvent.click(await row(dialog, 'Test Eva'));

    expect(dialog.getByText('0 places left')).toBeTruthy();
    expect((await row(dialog, 'Test Leo')).disabled).toBe(true);
    expect(dialog.getByText("That's 20 people, the most a group chat can have.")).toBeTruthy();
  });

  it('says who the backend refused, and the limit when someone else filled it meanwhile', async () => {
    let answer = json({ code: 'CANNOT_ADD_TO_GROUP_CHAT', params: { numbers: [eva.number] } }, 409);
    backend({ add: () => answer });
    open();
    const dialog = await startAdding();
    fireEvent.click(await row(dialog, 'Test Eva'));

    fireEvent.click(dialog.getByRole('button', { name: 'Add 1 person' }));
    expect(await dialog.findByText("You can't add Test Eva to a group chat.")).toBeTruthy();
    expect((await row(dialog, 'Test Eva')).textContent).toContain("Can't be added");

    answer = json({ code: 'TOO_MANY_GROUP_CHAT_MEMBERS', params: { max: 20 } }, 400);
    fireEvent.click(await row(dialog, 'Test Eva'));
    fireEvent.click(await row(dialog, 'Test Leo'));
    fireEvent.click(dialog.getByRole('button', { name: 'Add 1 person' }));
    expect(await dialog.findByText('A group chat can have at most 20 members, counting you.')).toBeTruthy();
  });

  it("isn't offered on an assignment group's chat", async () => {
    backend({ assignment: true });
    open();
    await screen.findByText('3 members');

    fireEvent.click(screen.getByRole('button', { name: 'Chat options' }));
    expect(await screen.findByRole('menuitem', { name: 'Members' })).toBeTruthy();
    expect(screen.queryByRole('menuitem', { name: 'Add members' })).toBeNull();
  });
});
