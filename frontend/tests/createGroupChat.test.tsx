import { act, cleanup, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { forgetConversations } from '../src/pages/chats/conversations';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { ChatContactData, ChatEvent, ChatSummaryData, MessageData } from '../src/types/chat';

// every listener of a destination gets what the test pushes
const live = vi.hoisted(() => ({ listeners: new Map<string, Set<(event: unknown) => void>>(), connected: false }));
vi.mock('../src/api/chatSocket', () => ({
  // down unless a test says so, then nobody shows as online
  isChatSocketConnected: () => live.connected, onChatSocketConnection: () => () => undefined, sendTyping: vi.fn(),
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

const group = (chatId: number, name: string, lastMessage: MessageData | null = null): ChatSummaryData =>
  ({ chatId, name, contact: null, picture: false, pictureUpdatedAt: null, lastMessage, unread: 0, assignment: false });
const message = (id: number, author: ChatContactData, text: string): MessageData =>
  ({ id, chatId: 5, author, text, sentAt: '2026-11-21T09:00:00', editedAt: null, reply: false, replyTo: null });
// 25 people to pick from, more than fit
const crowd = Array.from({ length: 25 }, (_, index): ChatContactData => ({ number: 60000 + index, name: `Test Person ${index}`, role: 'STUDENT' }));

interface Call { method: string; path: string; body?: unknown }

// the chat list, the people search and a group chat; `answer` takes the calls the test cares about
function backend({ chats = [] as ChatSummaryData[], people = [ana, rui, eva], answer = (() => undefined) as (call: Call) => Response | undefined } = {}) {
  const calls: Call[] = [];
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    const call = { method: init?.method ?? 'GET', path: url.pathname, body: init?.body ? JSON.parse(String(init.body)) : undefined };
    calls.push(call);
    const answered = answer(call);
    if (answered) return answered;
    if (url.pathname === '/api/users/chat-contacts') {
      const search = url.searchParams.get('search')?.toLowerCase();
      const found = people.filter(person => !search || person.name.toLowerCase().includes(search));
      return json({ content: found, totalElements: found.length, totalPages: 1, number: 0, size: 25 });
    }
    if (url.pathname === '/api/chats/groups') {
      const body = call.body as { name: string };
      return json(group(5, body.name), 201);
    }
    if (url.pathname === '/api/chats/5/members') return json([me, ana, rui]);
    if (url.pathname === '/api/chats/5/messages' && call.method === 'GET') return json({ messages: [], reactions: [], hasMore: false, hasNewer: false });
    if (url.pathname === '/api/chats/5/read') return new Response(null, { status: 204 });
    if (url.pathname === '/api/chats/') return json(chats);
    if (url.pathname === '/api/chats/unread-count') return json(0);
    throw new Error(`Unexpected request: ${call.method} ${url.pathname}`);
  }));
  return calls;
}

function open(path = '/chats') {
  render(<StudyMatchProvider><MemoryRouter initialEntries={[path]}><TeacherArea currentUser={{ ...me, institutionalEmail: 'teacher@school.pt', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

const dialog = () => within(screen.getByRole('dialog', { name: 'New group chat' }));
async function startCreating() {
  fireEvent.click(await screen.findByRole('button', { name: 'New group chat' }));
  return dialog();
}
// a person's row in the search, by the name in it (a picked one's chip has a button too)
const row = async (name: string) => (await dialog().findByText(name, { selector: '.sm-person__name' })).closest('button') as HTMLButtonElement;
const pick = async (name: string) => fireEvent.click(await row(name));
const create = () => fireEvent.click(dialog().getByRole('button', { name: 'Create group chat' }));
const created = (calls: Call[]) => calls.filter(call => call.path === '/api/chats/groups');

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { live.connected = false; cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); forgetConversations(); live.listeners.clear(); });

describe('create a group chat', () => {
  it('creates it with a name and the people picked, then opens it and adds it to the list', async () => {
    const calls = backend();
    open();
    const form = await startCreating();

    fireEvent.change(form.getByLabelText('Name'), { target: { value: '  Test Study Group ' } });
    await pick('Test Ana');
    await pick('Test Rui');

    expect(form.getByRole('list', { name: 'Picked people' }).textContent).toContain('Test Rui');
    expect(form.getByText('3', { selector: 'b' })).toBeTruthy();
    create();

    expect(await screen.findByRole('heading', { name: 'Test Study Group' })).toBeTruthy();
    expect(screen.getByText('3 members')).toBeTruthy();
    expect(created(calls)[0].body).toEqual({ name: 'Test Study Group', memberNumbers: [ana.number, rui.number], picture: null });
    expect(within(screen.getByRole('list', { name: 'Chats' })).getByRole('link').textContent).toContain('Test Study Group');
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'New group chat' })).toBeNull());
  });

  it('asks for a name and at least 2 people before asking the backend', async () => {
    const calls = backend();
    open();
    const form = await startCreating();
    await pick('Test Ana');

    create();

    expect(form.getByText('This field is required.')).toBeTruthy();
    expect(form.getByText('Pick at least 2 people for a group chat.')).toBeTruthy();
    expect(created(calls)).toEqual([]);

    // taking someone off and putting them back is a pick like any other
    fireEvent.click(form.getByRole('button', { name: 'Take Test Ana off' }));
    expect(form.queryByRole('list', { name: 'Picked people' })).toBeNull();
  });

  it('stops adding people at 20 members, counting you', async () => {
    backend({ people: crowd });
    open();
    const form = await startCreating();

    for (let index = 0; index < 19; index++) await pick(`Test Person ${index}`);

    expect(form.getByText('20', { selector: 'b' })).toBeTruthy();
    expect(form.getByText("That's 20 people, the most a group chat can have.")).toBeTruthy();
    expect((await row('Test Person 20')).disabled).toBe(true);
    // the picked ones can still be taken off
    const picked = await row('Test Person 3');
    expect(picked.disabled).toBe(false);
    fireEvent.click(picked);
    expect((await row('Test Person 20')).disabled).toBe(false);
  });

  it('says who the backend refused, next to them', async () => {
    backend({ answer: call => call.path === '/api/chats/groups' ? json({ code: 'CANNOT_ADD_TO_GROUP_CHAT', params: { numbers: [rui.number] } }, 409) : undefined });
    open();
    const form = await startCreating();
    fireEvent.change(form.getByLabelText('Name'), { target: { value: 'Test Study Group' } });
    await pick('Test Ana');
    await pick('Test Rui');

    create();

    expect(await form.findByText("You can't add Test Rui to a group chat.")).toBeTruthy();
    expect((await row('Test Rui')).textContent).toContain("Can't be added");
    expect(screen.getByRole('dialog', { name: 'New group chat' })).toBeTruthy();
  });

  it('shows the picture picked, and refuses one that is not a PNG or JPEG', async () => {
    backend();
    open();
    const form = await startCreating();
    const input = document.querySelector<HTMLInputElement>('.sm-chat__picture input[type=file]')!;

    fireEvent.change(input, { target: { files: [new File(['gif'], 'cat.gif', { type: 'image/gif' })] } });
    expect(form.getByText("Use a JPG or PNG image up to 1 MB for the group chat's picture.")).toBeTruthy();

    fireEvent.change(input, { target: { files: [new File(['png'], 'cat.png', { type: 'image/png' })] } });
    await waitFor(() => expect(document.querySelector('.sm-chat__picture img')?.getAttribute('src')).toMatch(/^data:image\/png;base64,/));
    expect(form.queryByText("Use a JPG or PNG image up to 1 MB for the group chat's picture.")).toBeNull();
    fireEvent.click(form.getByRole('button', { name: 'Take the picture off' }));
    expect(document.querySelector('.sm-chat__picture img')).toBeNull();
  });
});

describe('open a group chat', () => {
  it("shows its name, members and who wrote each message, and sends to the chat's id", async () => {
    const calls = backend({
      chats: [group(5, 'Test Study Group', message(51, rui, 'Hi all'))],
      answer: call => {
        if (call.path === '/api/chats/5/messages' && call.method === 'GET') return json({ messages: [message(50, ana, 'Hello there'), message(51, rui, 'Hi all')], reactions: [], hasMore: false, hasNewer: false });
        if (call.path === '/api/chats/5/messages' && call.method === 'POST') return json({ ...message(52, me, (call.body as { text: string }).text), sentAt: '2026-11-21T09:05:00' });
        return undefined;
      },
    });
    open('/chats/group/5');

    const messages = within(await screen.findByRole('log', { name: 'Messages' }));
    expect(await messages.findByText('Hello there')).toBeTruthy();
    expect(messages.getByText('Test Ana')).toBeTruthy();
    expect(messages.getByText('Test Rui')).toBeTruthy();
    expect(screen.getByText('3 members')).toBeTruthy();

    fireEvent.change(screen.getByRole('textbox', { name: 'Message Test Study Group' }), { target: { value: 'Hi everyone' } });
    fireEvent.keyDown(screen.getByRole('textbox', { name: 'Message Test Study Group' }), { key: 'Enter' });

    await waitFor(() => expect(calls.find(call => call.method === 'POST' && call.path === '/api/chats/5/messages')?.body).toEqual({ text: 'Hi everyone' }));
    expect(await messages.findByText('Hi everyone')).toBeTruthy();
  });

  it("shows a new message from someone in it live, and says so when it isn't yours", async () => {
    backend({ chats: [group(5, 'Test Study Group')] });
    open('/chats/group/5');
    expect(await screen.findByText('Nobody has written in Test Study Group yet, say hi to everyone.')).toBeTruthy();

    await waitFor(() => expect(live.listeners.get('/topic/chats/5')?.size).toBeGreaterThan(0));
    act(() => live.listeners.get('/topic/chats/5')?.forEach(listener => listener({ type: 'MESSAGE_CREATED', payload: message(53, eva, 'Joining in') } satisfies ChatEvent)));

    expect(await within(await screen.findByRole('log', { name: 'Messages' })).findByText('Joining in')).toBeTruthy();
    expect(screen.getByText('Test Eva')).toBeTruthy();
  });

  it("shows the list's error instead of loading forever when the list can't load", async () => {
    backend({ answer: call => call.path === '/api/chats/' ? json({ code: 'ACCESS_DENIED' }, 500) : undefined });
    open('/chats/group/5');

    await waitFor(() => expect(screen.getAllByText(i18n.t('errors.ACCESS_DENIED'))).toHaveLength(2));
    expect(screen.getByRole('link', { name: 'Back to chats' })).toBeTruthy();
  });

  it("says an address that can't be a group chat can't be opened", async () => {
    backend();
    open('/chats/group/abc');

    expect(await screen.findByText(i18n.t('errors.NOT_FOUND'))).toBeTruthy();
  });

  it('says how many of the others are online in its head', async () => {
    live.connected = true;
    backend({
      chats: [group(5, 'Test Study Group')],
      // Ana is online, and you are too, which doesn't count
      answer: call => call.path === '/api/chats/online' ? json([ana.number, me.number]) : undefined,
    });
    open('/chats/group/5');

    expect(await screen.findByText('3 members · 1 online')).toBeTruthy();
  });

  it("says a group chat you aren't in can't be opened", async () => {
    backend({ answer: call => call.path === '/api/chats/8/members' ? json({ code: 'NOT_FOUND' }, 404) : undefined });
    open('/chats/group/8');

    expect(await screen.findByText(i18n.t('errors.NOT_FOUND'))).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Back to chats' })).toBeTruthy();
  });
});
