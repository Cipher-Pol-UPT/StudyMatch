import { cleanup, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { forgetConversations } from '../src/pages/chats/conversations';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { ChatContactData, MessageData } from '../src/types/chat';

vi.mock('../src/api/chatSocket', () => ({ subscribeToChatEvents: () => () => undefined }));

configure({ asyncUtilTimeout: 5000 });
vi.setConfig({ testTimeout: 15000 });

const me: ChatContactData = { number: 9, name: 'Test Teacher', role: 'TEACHER' };
const ana: ChatContactData = { number: 50001, name: 'Test Ana', role: 'STUDENT' };
const CHAT = 7;
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
const message = (id: number, author: ChatContactData, text: string, sentAt = '2026-11-21T09:00:00'): MessageData =>
  ({ id, chatId: CHAT, author, text, sentAt, editedAt: null, reply: false, replyTo: null });

const latest = message(90, ana, 'See you tomorrow');

// the DM with Ana; `answer` takes the searches and the jumps
function backend(answer: (url: URL) => Response | undefined) {
  const calls: string[] = [];
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async input => {
    const url = new URL(String(input), 'https://test.invalid');
    calls.push(`${url.pathname}${url.search}`);
    const answered = answer(url);
    if (answered) return answered;
    if (url.pathname === `/api/chats/${CHAT}/messages`) return json({ messages: [latest], reactions: [], hasMore: true, hasNewer: false });
    if (url.pathname === `/api/chats/direct/${ana.number}`) return json({ chatId: CHAT, contact: ana, blocked: false });
    if (url.pathname === `/api/chats/${CHAT}/read`) return new Response(null, { status: 204 });
    if (url.pathname === '/api/chats/') return json([]);
    if (url.pathname === '/api/chats/unread-count') return json(0);
    throw new Error(`Unexpected request: ${url.pathname}`);
  }));
  return calls;
}
const searches = (calls: string[]) => calls.filter(call => call.includes('/messages/search'));

function open() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={[`/chats/direct/${ana.number}`]}><TeacherArea currentUser={{ ...me, institutionalEmail: 'teacher@school.pt', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

async function search(text: string) {
  await screen.findByRole('log', { name: 'Messages' });
  fireEvent.click(screen.getByRole('button', { name: 'Search this chat' }));
  const field = screen.getByRole('searchbox', { name: 'Search this chat' });
  expect(document.activeElement).toBe(field);
  fireEvent.change(field, { target: { value: text } });
  return field;
}
const panel = () => within(screen.getByRole('complementary', { name: 'Search results' }));

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); forgetConversations(); });

describe('search a chat', () => {
  it('shows the results newest first with who, when and the matching words highlighted', async () => {
    const calls = backend(url => url.pathname.endsWith('/search') ? json({
      messages: [message(40, me, 'The REPORT is done', '2026-11-20T18:00:00'), message(12, ana, 'Can you check my report?', '2026-11-19T10:00:00')],
      hasMore: false,
    }) : undefined);
    open();

    await search('  report ');

    const results = within(await panel().findByRole('list', { name: 'Search results' })).getAllByRole('button');
    expect(results.map(result => result.querySelector('b')?.textContent)).toEqual(['You', 'Test Ana']);
    expect([...results[0].querySelectorAll('mark')].map(mark => mark.textContent)).toEqual(['REPORT']);
    expect(results[1].querySelector('time')?.textContent).toBe('Nov 19, 2026');
    expect(panel().getByRole('heading').textContent).toBe('2 results');
    // beside the messages, not over the message box
    const aside = screen.getByRole('complementary', { name: 'Search results' });
    expect(aside.closest('.sm-chat__scroll')).toBeNull();
    expect(aside.parentElement?.lastElementChild?.contains(screen.getByRole('textbox', { name: 'Message Test Ana' }))).toBe(true);
    // without the spaces around it
    expect(searches(calls)).toEqual([`/api/chats/${CHAT}/messages/search?text=report`]);
  });

  it('waits until you stop typing, and does not search with fewer than 2 characters', async () => {
    const calls = backend(url => url.pathname.endsWith('/search') ? json({ messages: [], hasMore: false }) : undefined);
    open();
    const field = await search('r');

    expect(panel().getByText('Type at least 2 characters to search.')).toBeTruthy();
    fireEvent.change(field, { target: { value: 're' } });
    fireEvent.change(field, { target: { value: 'rep' } });
    fireEvent.change(field, { target: { value: 'repo' } });

    await waitFor(() => expect(searches(calls)).toHaveLength(1));
    expect(searches(calls)[0]).toBe(`/api/chats/${CHAT}/messages/search?text=repo`);
  });

  it('says so in the panel when nothing matches', async () => {
    backend(url => url.pathname.endsWith('/search') ? json({ messages: [], hasMore: false }) : undefined);
    open();

    await search('pizza');

    expect(await panel().findByText('No messages found')).toBeTruthy();
    expect(panel().getByText('Nothing in this chat says “pizza”.')).toBeTruthy();
  });

  it('loads the next 25 from the oldest result', async () => {
    const calls = backend(url => {
      if (!url.pathname.endsWith('/search')) return undefined;
      return url.searchParams.get('before') === '40'
        ? json({ messages: [message(12, ana, 'Can you check my report?')], hasMore: false })
        : json({ messages: [message(40, me, 'The report is done')], hasMore: true });
    });
    open();
    await search('report');

    expect(await panel().findByRole('heading', { name: 'More than 1 results' })).toBeTruthy();
    fireEvent.click(panel().getByRole('button', { name: 'Show more' }));

    expect(await panel().findByText('Can you check my', { exact: false })).toBeTruthy();
    expect(searches(calls)).toContain(`/api/chats/${CHAT}/messages/search?text=report&before=40`);
  });

  it('jumps to a result: the chat loads around it and highlights it', async () => {
    const found = message(12, ana, 'Can you check my report?');
    const calls = backend(url => {
      if (url.pathname.endsWith('/search')) return json({ messages: [found], hasMore: false });
      if (url.pathname === `/api/chats/${CHAT}/messages/12/around`) return json({ messages: [found, message(13, me, 'Sure')], reactions: [], hasMore: true, hasNewer: true });
      return undefined;
    });
    open();
    await search('report');

    fireEvent.click(await panel().findByRole('button', { name: /Can you check my/ }));

    await waitFor(() => expect(document.getElementById('message-12')?.classList).toContain('sm-msg--flash'));
    expect(calls).toContain(`/api/chats/${CHAT}/messages/12/around`);
    // the way back to the latest, in the middle of the messages the results leave on screen
    expect(screen.getByRole('button', { name: 'Back to the latest' }).closest('.sm-chat__thread')?.classList).toContain('sm-chat__thread--side');
    expect(document.getElementById('message-90')).toBeNull();
  });

  it('says in the panel when the message was deleted before you could jump to it', async () => {
    backend(url => {
      if (url.pathname.endsWith('/search')) return json({ messages: [message(12, ana, 'Can you check my report?')], hasMore: false });
      if (url.pathname.endsWith('/around')) return json({ code: 'NOT_FOUND' }, 404);
      return undefined;
    });
    open();
    await search('report');

    fireEvent.click(await panel().findByRole('button', { name: /Can you check my/ }));

    expect(await panel().findByText("That message isn't there anymore.")).toBeTruthy();
  });

  it('shows what the backend says about a search in the panel', async () => {
    backend(url => url.pathname.endsWith('/search') ? json({ code: 'SEARCH_TOO_SHORT', params: { min: 2 } }, 400) : undefined);
    open();

    await search('ab');

    expect(await panel().findByText('Type at least 2 characters to search.')).toBeTruthy();
  });

  it('closes with the X, or with Esc once the field is empty', async () => {
    backend(url => url.pathname.endsWith('/search') ? json({ messages: [], hasMore: false }) : undefined);
    open();
    const field = await search('pizza');

    fireEvent.keyDown(field, { key: 'Escape' });
    expect(screen.getByRole('searchbox', { name: 'Search this chat' })).toBeTruthy();
    fireEvent.keyDown(screen.getByRole('searchbox', { name: 'Search this chat' }), { key: 'Escape' });
    expect(screen.queryByRole('complementary', { name: 'Search results' })).toBeNull();

    await search('pizza');
    fireEvent.click(panel().getByRole('button', { name: 'Close search' }));
    expect(screen.queryByRole('complementary', { name: 'Search results' })).toBeNull();
  });
});
