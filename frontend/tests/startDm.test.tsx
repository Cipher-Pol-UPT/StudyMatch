import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { StudentArea } from '../src/pages/student/StudentArea';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { ChatContactData, DirectChatData } from '../src/types/chat';

const ana: ChatContactData = { number: 50001, name: 'Test Ana', role: 'STUDENT' };
const rui: ChatContactData = { number: 9001, name: 'Test Rui', role: 'TEACHER' };
const page = (content: ChatContactData[], totalElements = content.length) => ({ content, page: 0, size: 20, totalElements, totalPages: 1 });
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

// the contact search filters like the backend does, by name or number; the DM lookup is answered by the test
function backend(direct: (number: number) => Response = number => reply({ chatId: null, contact: number === ana.number ? ana : rui } satisfies DirectChatData)) {
  const searches: string[] = [];
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async input => {
    const url = new URL(String(input), 'https://test.invalid');
    if (url.pathname === '/api/users/chat-contacts') {
      const search = (url.searchParams.get('search') ?? '').toLowerCase();
      searches.push(search);
      return reply(page([ana, rui].filter(contact => contact.name.toLowerCase().includes(search) || String(contact.number).includes(search))));
    }
    const dm = url.pathname.match(/^\/api\/chats\/direct\/(\d+)$/);
    if (dm) return direct(Number(dm[1]));
    // the student area loads its own profile for the menu; this test doesn't need it
    if (url.pathname === '/api/students/me/profile') return reply({ code: 'NOT_FOUND' }, 404);
    throw new Error(`Unexpected request: ${url.pathname}`);
  }));
  return searches;
}

function open(path = '/chats') {
  render(<StudyMatchProvider><MemoryRouter initialEntries={[path]}><TeacherArea currentUser={{ number: 9, name: 'Test Teacher', institutionalEmail: 'teacher@school.pt', role: 'TEACHER', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

async function newMessage() {
  fireEvent.click(await screen.findByRole('button', { name: 'New message' }));
  return within(await screen.findByRole('dialog'));
}

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('start a DM', () => {
  it('searches by name', async () => {
    const searches = backend();
    open();
    const dialog = await newMessage();
    expect(await dialog.findByText('Test Rui')).toBeTruthy();

    fireEvent.change(dialog.getByRole('searchbox', { name: 'Search people' }), { target: { value: 'ana' } });

    await waitFor(() => expect(dialog.queryByText('Test Rui')).toBeNull());
    expect(dialog.getByText('Test Ana')).toBeTruthy();
    expect(dialog.getByText('Student · 50001')).toBeTruthy();
    expect(searches).toContain('ana');
  });

  it('searches by number', async () => {
    backend();
    open();
    const dialog = await newMessage();

    fireEvent.change(await dialog.findByRole('searchbox', { name: 'Search people' }), { target: { value: '9001' } });

    await waitFor(() => expect(dialog.queryByText('Test Ana')).toBeNull());
    expect(dialog.getByText('Test Rui')).toBeTruthy();
    expect(dialog.getByText('Teacher · 9001')).toBeTruthy();
  });

  it('says so when no one matches', async () => {
    backend();
    open();
    const dialog = await newMessage();

    fireEvent.change(await dialog.findByRole('searchbox', { name: 'Search people' }), { target: { value: 'nobody' } });

    expect(await dialog.findByText('No one found')).toBeTruthy();
  });

  it('opens an empty conversation with someone you never talked to, with a hint to say hi', async () => {
    backend();
    open();
    const dialog = await newMessage();

    fireEvent.click(await dialog.findByRole('button', { name: /Test Ana/ }));

    const conversation = within(await screen.findByRole('region', { name: 'Conversation with Test Ana' }));
    expect(conversation.getByRole('heading', { level: 2, name: 'Test Ana' })).toBeTruthy();
    expect(conversation.getByText("Student · 50001. You haven't talked yet, say hi to Test.")).toBeTruthy();
    // the DM isn't in the list until its first message
    expect(screen.getByText('No chats yet')).toBeTruthy();
  });

  it('opens the DM you already have, without the hint', async () => {
    backend(() => reply({ chatId: 12, contact: rui } satisfies DirectChatData));
    open('/chats/direct/9001');

    const conversation = within(await screen.findByRole('region', { name: 'Conversation with Test Rui' }));

    expect(conversation.getByText('Teacher · 9001')).toBeTruthy();
    expect(conversation.queryByText(/say hi/)).toBeNull();
  });

  it("shows the backend's error in the conversation", async () => {
    backend(() => reply({ code: 'NOT_FOUND' }, 404));
    open('/chats/direct/123');

    expect(await screen.findByText(i18n.t('errors.NOT_FOUND'))).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Back to chats' })).toBeTruthy();
  });

  it('a student opens Chats and starts a DM too', async () => {
    backend();
    render(<StudyMatchProvider><MemoryRouter initialEntries={['/chats']}><StudentArea currentUser={{ number: 50002, name: 'Test Student', institutionalEmail: 'student@school.pt', role: 'STUDENT', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
    const dialog = await newMessage();

    fireEvent.click(await dialog.findByRole('button', { name: /Test Rui/ }));

    expect(await screen.findByRole('region', { name: 'Conversation with Test Rui' })).toBeTruthy();
    expect(screen.queryByText(i18n.t('placeholder.comingSoon'))).toBeNull();
  });

  it('without a conversation open, asks to pick one', async () => {
    backend();
    open();

    expect(await screen.findByText('Pick a chat')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Chats' })).toBeTruthy();
  });
});
