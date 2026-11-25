import { act, cleanup, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { forgetConversations } from '../src/pages/chats/conversations';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { ChatContactData, MessageData } from '../src/types/chat';

// these render a whole area, which takes longer than the default second when every test file runs at once
configure({ asyncUtilTimeout: 5000 });
vi.setConfig({ testTimeout: 15000 });

const ana: ChatContactData = { number: 50001, name: 'Test Ana', role: 'STUDENT' };
const rui: ChatContactData = { number: 9001, name: 'Test Rui', role: 'TEACHER' };
const me: ChatContactData = { number: 9, name: 'Test Teacher', role: 'TEACHER' };
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

// the DM lookup always says you've never talked; each send is answered by `answer`, which gets the text sent
function backend(answer: (text: string) => Promise<Response> = async text => reply(sent(text), 201)) {
  const sends: { number: number; text: string }[] = [];
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    if (url.pathname === '/api/users/chat-contacts') return reply({ content: [ana, rui], page: 0, size: 20, totalElements: 2, totalPages: 1 });
    const send = url.pathname.match(/^\/api\/chats\/direct\/(\d+)\/messages$/);
    if (send && init?.method === 'POST') {
      const { text } = JSON.parse(String(init.body)) as { text: string };
      sends.push({ number: Number(send[1]), text });
      return answer(text);
    }
    // the first message creates the DM, whose history then loads: what was just sent is all there is
    if (url.pathname === '/api/chats/7/messages') return reply({ hasMore: false, messages: sends.length ? [sent(sends.at(-1)!.text)] : [] });
    if (url.pathname === '/api/chats/7/read') return new Response(null, { status: 204 });
    const dm = url.pathname.match(/^\/api\/chats\/direct\/(\d+)$/);
    if (dm) return reply({ chatId: null, contact: Number(dm[1]) === ana.number ? ana : rui });
    // the chat list and the side menu's count, both empty here
    if (url.pathname === '/api/chats/') return new Response('[]', { status: 200 });
    if (url.pathname === '/api/chats/unread-count') return new Response('0', { status: 200 });
    throw new Error(`Unexpected request: ${url.pathname}`);
  }));
  return sends;
}

const sent = (text: string): MessageData => ({ id: 1, chatId: 7, author: me, text, sentAt: '2026-11-21T10:30:00', editedAt: null });

function open(path = `/chats/direct/${ana.number}`) {
  render(<StudyMatchProvider><MemoryRouter initialEntries={[path]}><TeacherArea currentUser={{ number: 9, name: 'Test Teacher', institutionalEmail: 'teacher@school.pt', role: 'TEACHER', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

const box = (name = 'Test Ana') => screen.findByRole('textbox', { name: `Message ${name}` });
// waits until the box shows the text, so what follows (Enter, the count) sees it
async function type(field: HTMLElement, text: string) {
  fireEvent.change(field, { target: { value: text } });
  await waitFor(() => expect((field as HTMLTextAreaElement).value).toBe(text));
}
const enter = (field: HTMLElement, shiftKey = false) => fireEvent.keyDown(field, { key: 'Enter', shiftKey });
// lets a send that wrongly started reach the backend stub, and the page render, before checking nothing was sent
const settle = () => act(() => new Promise(resolve => setTimeout(resolve, 50)));

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); forgetConversations(); });

describe('send a message', () => {
  it('greys the message out while sending, and shows it once the backend confirms it', async () => {
    let confirm: (response: Response) => void = () => undefined;
    const sends = backend(() => new Promise(resolve => { confirm = resolve; }));
    open();
    const field = await box();

    await type(field, '  Hello Ana  ');
    enter(field);

    const log = within(await screen.findByRole('log', { name: 'Messages' }));
    expect(log.getByText('Hello Ana')).toBeTruthy();
    expect(log.getByText('Sending…')).toBeTruthy();
    expect((field as HTMLTextAreaElement).value).toBe('');
    expect(sends).toEqual([{ number: ana.number, text: 'Hello Ana' }]);

    confirm(reply(sent('Hello Ana'), 201));

    await waitFor(() => expect(log.queryByText('Sending…')).toBeNull());
    expect(log.getByText('Hello Ana')).toBeTruthy();
    expect(screen.getByText('This is the start of your conversation with Test.')).toBeTruthy();
  });

  it('sends with the Send button too', async () => {
    const sends = backend();
    open();
    await type(await box(), 'Hi');

    fireEvent.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() => expect(sends).toHaveLength(1));
  });

  it('adds a line with Shift+Enter instead of sending', async () => {
    const sends = backend();
    open();
    const field = await box();
    await type(field, 'First line');

    enter(field, true);
    await settle();

    expect(sends).toHaveLength(0);
    expect((field as HTMLTextAreaElement).value).toBe('First line');
    expect(screen.queryByRole('log')).toBeNull();
  });

  it('does nothing with an empty box', async () => {
    const sends = backend();
    open();
    const field = await box();
    await type(field, '   ');

    enter(field);
    await settle();

    expect(sends).toHaveLength(0);
    expect((screen.getByRole('button', { name: 'Send' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('counts near the 2000 character limit and won\'t send past it', async () => {
    const sends = backend();
    open();
    const field = await box();

    await type(field, 'a'.repeat(1700));
    expect(screen.queryByText('1700 / 2000')).toBeNull();

    await type(field, 'a'.repeat(1900));
    expect(await screen.findByText('1900 / 2000')).toBeTruthy();

    await type(field, 'a'.repeat(2001));
    enter(field);
    await settle();

    expect(screen.getByText('2001 / 2000')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Send' }) as HTMLButtonElement).disabled).toBe(true);
    expect(sends).toHaveLength(0);
  });

  it('leaves the spaces around the text out of the count, like the backend', async () => {
    const sends = backend();
    open();
    const field = await box();

    await type(field, `  ${'a'.repeat(2000)}\n`);
    enter(field);

    expect(screen.queryByText('2003 / 2000')).toBeNull();
    await waitFor(() => expect(sends).toEqual([{ number: ana.number, text: 'a'.repeat(2000) }]));
  });

  it('keeps a failed message with Retry, which sends it again', async () => {
    let fail = true;
    const sends = backend(async text => fail ? Promise.reject(new TypeError('Failed to fetch')) : reply(sent(text), 201));
    open();
    const field = await box();
    await type(field, 'Are you there?');
    enter(field);

    expect(await screen.findByText('Not sent')).toBeTruthy();
    expect(screen.getByRole('alert').textContent).toBe('Something went wrong. Please try again.');

    fail = false;
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));

    await waitFor(() => expect(screen.queryByText('Not sent')).toBeNull());
    expect(screen.queryByRole('alert')).toBeNull();
    expect(sends.map(s => s.text)).toEqual(['Are you there?', 'Are you there?']);
  });

  it('drops a failed message with Discard', async () => {
    backend(async () => reply({ code: 'NOT_FOUND' }, 404));
    open();
    const field = await box();
    await type(field, 'Hello?');
    enter(field);
    await screen.findByText('Not sent');

    fireEvent.click(screen.getByRole('button', { name: 'Discard' }));

    expect(screen.queryByText('Hello?')).toBeNull();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it("shows the backend's error under the message box", async () => {
    backend(async () => reply({ code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'text', code: 'NOT_BLANK' }] }, 400));
    open();
    const field = await box();
    await type(field, 'Hello');
    enter(field);

    expect((await screen.findByRole('alert')).textContent).toBe(i18n.t('validation.NOT_BLANK'));
    expect(field.getAttribute('aria-invalid')).toBe('true');
  });

  it("keeps the draft when you switch to another chat and back", async () => {
    backend();
    open();
    await type(await box(), 'Half a thought');

    // the list's own, the empty list has one too
    fireEvent.click(screen.getAllByRole('button', { name: 'New message' })[0]);
    fireEvent.click(await within(await screen.findByRole('dialog')).findByRole('button', { name: /Test Rui/ }));
    expect(((await box('Test Rui')) as HTMLTextAreaElement).value).toBe('');

    // the list's own, the empty list has one too
    fireEvent.click(screen.getAllByRole('button', { name: 'New message' })[0]);
    fireEvent.click(await within(await screen.findByRole('dialog')).findByRole('button', { name: /Test Ana/ }));

    expect(((await box()) as HTMLTextAreaElement).value).toBe('Half a thought');
  });

  it('a send that fails after logout leaves no error for the next person', async () => {
    let fail: (error: Error) => void = () => undefined;
    backend(() => new Promise((_, reject) => { fail = reject; }));
    open();
    const field = await box();
    await type(field, 'Bye');
    enter(field);
    await screen.findByText('Sending…');

    cleanup();
    forgetConversations();
    fail(new TypeError('Failed to fetch'));
    await new Promise(resolve => setTimeout(resolve, 50));
    open();

    await box();
    expect(screen.queryByRole('alert')).toBeNull();
  });
});
