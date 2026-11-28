import { act, cleanup, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { forgetConversations } from '../src/pages/chats/conversations';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { ChatContactData, ChatEditedData, ChatEvent, ChatSummaryData } from '../src/types/chat';

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
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
const group = (changes: Partial<ChatSummaryData> = {}): ChatSummaryData =>
  ({ chatId: 5, name: 'Test Study Group', contact: null, picture: false, pictureUpdatedAt: null, lastMessage: null, unread: 0, assignment: false, ...changes });

interface Call { method: string; path: string; body?: unknown }

// a group chat; `members` is who's in it, the owner first. `answer` takes the calls the test cares about
function backend({ chat = group(), members = [ana, me, rui], answer = () => undefined }: { chat?: ChatSummaryData; members?: ChatContactData[]; answer?: (call: Call) => Response | undefined } = {}) {
  const calls: Call[] = [];
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    const call = { method: init?.method ?? 'GET', path: url.pathname, body: init?.body ? JSON.parse(String(init.body)) : undefined };
    calls.push(call);
    const answered = answer(call);
    if (answered) return answered;
    if (call.method === 'PUT' && (url.pathname === '/api/chats/5/name' || url.pathname === '/api/chats/5/picture')) return new Response(null, { status: 204 });
    if (url.pathname === '/api/chats/5/members') return json(members);
    if (url.pathname === '/api/chats/5/messages') return json({ messages: [], reactions: [], hasMore: false, hasNewer: false });
    if (url.pathname === '/api/chats/5/read') return new Response(null, { status: 204 });
    if (url.pathname === '/api/chats/') return json([chat]);
    if (url.pathname === '/api/chats/unread-count') return json(0);
    if (url.pathname === '/api/blocks/') return json([]);
    throw new Error(`Unexpected request: ${call.method} ${url.pathname}`);
  }));
  return calls;
}

function open() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={['/chats/group/5']}><TeacherArea currentUser={{ ...me, institutionalEmail: 'teacher@school.pt', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

async function startEditing() {
  await screen.findByText('3 members');
  fireEvent.click(await screen.findByRole('button', { name: 'Chat options' }));
  fireEvent.click(await screen.findByRole('menuitem', { name: 'Edit group chat' }));
  return within(await screen.findByRole('dialog', { name: 'Edit group chat' }));
}
const puts = (calls: Call[]) => calls.filter(call => call.method === 'PUT' && !call.path.endsWith('/read')).map(call => [call.path, call.body]);
// the chat's picture in the list row and the head, by its address
const pictures = () => [...document.querySelectorAll<HTMLImageElement>('img')].map(img => img.getAttribute('src')).filter(src => src?.startsWith('/api/chats/5/picture'));
const heading = () => document.querySelector('.sm-chat__head-name')?.textContent ?? '';
const listRow = () => document.querySelector('a[href="/chats/group/5"]')?.textContent ?? '';
const pick = (dialog: ReturnType<typeof within>, file: File) => {
  const input = dialog.getByRole('button', { name: /picture/ }).parentElement!.querySelector('input[type=file]')!;
  fireEvent.change(input, { target: { files: [file] } });
};

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); forgetConversations(); live.listeners.clear(); });

describe('edit a group chat', () => {
  it('lets any member rename it, and the head and the list show the new name', async () => {
    const calls = backend();
    open();
    const dialog = await startEditing();

    expect(dialog.getByText('Everyone in it sees the new name. Only the owner changes the picture.')).toBeTruthy();
    expect(dialog.queryByRole('button', { name: /picture/ })).toBeNull();
    fireEvent.change(dialog.getByRole('textbox', { name: 'Name' }), { target: { value: '  Databases crew  ' } });
    fireEvent.click(dialog.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Group chat updated.')).toBeTruthy();
    expect(puts(calls)).toEqual([['/api/chats/5/name', { name: 'Databases crew' }]]);
    await waitFor(() => expect(heading()).toContain('Databases crew'));
    expect(listRow()).toContain('Databases crew');
  });

  it('lets only the owner change the picture, and the new one gets a new address', async () => {
    const calls = backend({ members: [me, ana, rui] });
    open();
    const dialog = await startEditing();

    expect(dialog.getByText('Everyone in it sees the new name and picture.')).toBeTruthy();
    pick(dialog, new File(['png'], 'group.png', { type: 'image/png' }));
    await waitFor(() => expect(dialog.getByRole('button', { name: 'Change the picture' })).toBeTruthy());
    fireEvent.click(dialog.getByRole('button', { name: 'Save' }));

    await screen.findByText('Group chat updated.');
    // the name didn't change, so only the picture is sent
    expect(puts(calls)).toEqual([['/api/chats/5/picture', { picture: 'data:image/png;base64,cG5n' }]]);
    await waitFor(() => expect(pictures().length).toBeGreaterThan(0));
    expect(pictures().every(src => /\?v=\d{4}-/.test(src!))).toBe(true);
  });

  it('takes the picture off', async () => {
    const calls = backend({ chat: group({ picture: true, pictureUpdatedAt: '2026-11-20T10:00:00' }), members: [me, ana, rui] });
    open();
    await waitFor(() => expect(pictures()).toContain('/api/chats/5/picture?v=2026-11-20T10:00:00'));
    const dialog = await startEditing();

    fireEvent.click(dialog.getByRole('button', { name: 'Take the picture off' }));
    fireEvent.click(dialog.getByRole('button', { name: 'Save' }));

    await screen.findByText('Group chat updated.');
    expect(puts(calls)).toEqual([['/api/chats/5/picture', { picture: null }]]);
    await waitFor(() => expect(pictures()).toEqual([]));
  });

  it('refuses a picture that is too big or not a JPG or PNG before asking the backend', async () => {
    const calls = backend({ members: [me, ana, rui] });
    open();
    const dialog = await startEditing();

    pick(dialog, new File(['gif'], 'group.gif', { type: 'image/gif' }));
    expect(await dialog.findByText("Use a JPG or PNG image up to 1 MB for the group chat's picture.")).toBeTruthy();
    fireEvent.click(dialog.getByRole('button', { name: 'Save' }));

    expect(puts(calls)).toEqual([]);
  });

  it("shows the backend's errors next to the field", async () => {
    backend({ members: [me, ana, rui], answer: call => {
      if (call.path === '/api/chats/5/name') return json({ code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'name', code: 'SIZE_MAX', params: { max: 50 } }] }, 400);
      if (call.path === '/api/chats/5/picture') return json({ code: 'INVALID_GROUP_CHAT_PICTURE' }, 400);
      return undefined;
    } });
    open();
    const dialog = await startEditing();
    fireEvent.change(dialog.getByRole('textbox', { name: 'Name' }), { target: { value: 'Too long' } });
    fireEvent.click(dialog.getByRole('button', { name: 'Save' }));
    expect(await dialog.findByText('Use at most 50 characters or items.')).toBeTruthy();

    fireEvent.change(dialog.getByRole('textbox', { name: 'Name' }), { target: { value: 'Test Study Group' } });
    pick(dialog, new File(['png'], 'group.png', { type: 'image/png' }));
    await waitFor(() => expect(dialog.getByRole('button', { name: 'Change the picture' })).toBeTruthy());
    fireEvent.click(dialog.getByRole('button', { name: 'Save' }));
    expect(await dialog.findByText("Use a JPG or PNG image up to 1 MB for the group chat's picture.")).toBeTruthy();
  });

  it('asks for a name, and sends nothing when nothing changed', async () => {
    const calls = backend();
    open();
    let dialog = await startEditing();

    fireEvent.change(dialog.getByRole('textbox', { name: 'Name' }), { target: { value: '   ' } });
    fireEvent.click(dialog.getByRole('button', { name: 'Save' }));
    expect(await dialog.findByText('This field is required.')).toBeTruthy();
    fireEvent.click(dialog.getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Edit group chat' })).toBeNull());

    dialog = await startEditing();
    fireEvent.click(dialog.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Edit group chat' })).toBeNull());
    expect(puts(calls)).toEqual([]);
  });

  it('swaps the name and picture when someone else edits it', async () => {
    backend();
    open();
    await screen.findByText('3 members');

    const edited: ChatEditedData = { chatId: 5, name: 'Renamed by Ana', picture: true, pictureUpdatedAt: '2026-11-28T10:00:00' };
    act(() => live.listeners.get('/user/queue/chats')?.forEach(listener => listener({ type: 'CHAT_EDITED', payload: edited } satisfies ChatEvent)));

    await waitFor(() => expect(heading()).toContain('Renamed by Ana'));
    expect(listRow()).toContain('Renamed by Ana');
    await waitFor(() => expect(pictures()).toContain('/api/chats/5/picture?v=2026-11-28T10:00:00'));
  });

  it("isn't offered on an assignment group's chat, its name and picture come from the assignment", async () => {
    backend({ chat: group({ assignment: true }) });
    open();
    await screen.findByText('3 members');

    // the menu may have other entries, like Members
    const options = screen.queryByRole('button', { name: 'Chat options' });
    if (options) fireEvent.click(options);
    expect(screen.queryByRole('menuitem', { name: 'Edit group chat' })).toBeNull();
  });

  it('asks for a picture without ?v= when it has no date', async () => {
    backend({ chat: group({ picture: true, pictureUpdatedAt: null }) });
    open();

    await waitFor(() => expect(pictures()).toContain('/api/chats/5/picture'));
    expect(pictures().every(src => src === '/api/chats/5/picture')).toBe(true);
  });
});
