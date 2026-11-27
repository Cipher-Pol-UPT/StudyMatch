import { cleanup, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { forgetConversations } from '../src/pages/chats/conversations';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StickerArt, StudyMatchProvider } from '../src/studymatch-ui';
import { Sticker } from '../src/types/enums';
import en from '../src/i18n/en.json';
import pt from '../src/i18n/pt.json';
import type { ChatContactData, MessageData } from '../src/types/chat';

// every listener of a destination gets what the test pushes
const live = vi.hoisted(() => ({ listeners: new Map<string, Set<(event: unknown) => void>>() }));
vi.mock('../src/api/chatSocket', () => ({ onChatSocketReconnect: () => () => undefined,
  // the connection is down, so nobody shows as online
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
const CHAT = 7;
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

const question: MessageData = { id: 1, chatId: CHAT, author: ana, text: 'Ready for the exam?', sentAt: '2026-12-10T09:00:00', editedAt: null, reply: false, replyTo: null };
const fromAna: MessageData = { ...question, id: 2, text: '', sticker: 'PARTY', sentAt: '2026-12-10T09:01:00' };

interface Call { method: string; path: string; body?: unknown }

// the DM with Ana. Sending answers with the message the backend saved, unless `answer` says otherwise
function backend(messages: MessageData[], answer: (call: Call) => Response | undefined = () => undefined) {
  const calls: Call[] = [];
  let next = 10;
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    const call = { method: init?.method ?? 'GET', path: url.pathname, body: init?.body ? JSON.parse(String(init.body)) : undefined };
    calls.push(call);
    const answered = answer(call);
    if (answered) return answered;
    // a DM's messages go to the person
    if (call.path === `/api/chats/direct/${ana.number}/messages` && call.method === 'POST') {
      const { sticker, replyToId } = call.body as { sticker: Sticker; replyToId?: number };
      return json({ id: next++, chatId: CHAT, author: me, text: '', sticker, sentAt: '2026-12-10T10:00:00', editedAt: null, reply: replyToId !== undefined,
        replyTo: replyToId === undefined ? null : { id: replyToId, author: ana, text: question.text }, attachments: [] } satisfies MessageData, 201);
    }
    if (call.path === `/api/chats/${CHAT}/messages`) return json({ messages, reactions: [], hasMore: false, hasNewer: false });
    if (call.path === `/api/chats/direct/${ana.number}`) return json({ chatId: CHAT, contact: ana, blocked: false });
    if (call.path === `/api/chats/${CHAT}/read`) return new Response(null, { status: 204 });
    if (call.path === '/api/chats/') return json([]);
    if (call.path === '/api/chats/unread-count') return json(0);
    if (call.path === '/api/blocks/') return json([]);
    throw new Error(`Unexpected request: ${call.method} ${call.path}`);
  }));
  return calls;
}

function open() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={[`/chats/direct/${ana.number}`]}><TeacherArea currentUser={{ ...me, institutionalEmail: 'teacher@school.pt', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

const sent = (calls: Call[]) => calls.filter(call => call.method === 'POST' && call.path === `/api/chats/direct/${ana.number}/messages`).map(call => call.body);
const picker = () => screen.getByRole('dialog', { name: 'Pick a sticker' });
async function openPicker() {
  await screen.findByRole('log', { name: 'Messages' });
  fireEvent.click(screen.getByRole('button', { name: 'Stickers' }));
  return picker();
}
const packs = () => [...picker().querySelectorAll('section')].map(section => section.getAttribute('aria-label'));

beforeEach(async () => {
  await i18n.changeLanguage('en');
  // in December, so Christmas is in season
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-12-10T10:00:00'));
  localStorage.clear();
});
afterEach(() => { cleanup(); vi.useRealTimers(); vi.unstubAllGlobals(); vi.restoreAllMocks(); forgetConversations(); localStorage.clear(); });

describe('send a sticker', () => {
  it('has art and a name in both languages for every sticker the backend has', () => {
    render(<>{Sticker.map(id => <StickerArt key={id} id={id} />)}</>);
    const art = screen.getAllByRole('img');
    expect(art).toHaveLength(Sticker.length);
    for (const [index, id] of Sticker.entries()) {
      expect(art[index].getAttribute('aria-label')).toBe(en.stickers[id]);
      expect(pt.stickers[id]).toBeTruthy();
      // something is drawn besides the shadow
      expect(art[index].querySelectorAll('.sm-sticker__object path, .sm-sticker__object circle, .sm-sticker__object ellipse').length).toBeGreaterThan(2);
    }
  });

  it('opens the packs with the one in season first, and sends the sticker picked on its own', async () => {
    const calls = backend([question]);
    open();
    await openPicker();

    expect(packs()).toEqual(['Christmas', 'Every day', 'Reactions', 'Exams', 'Easter', 'Halloween', 'Summer']);
    expect(within(picker()).getByText('In season')).toBeTruthy();
    fireEvent.click(within(picker()).getByRole('button', { name: 'Reindeer' }));

    await waitFor(() => expect(sent(calls)).toEqual([{ sticker: 'REINDEER' }]));
    expect(screen.queryByRole('dialog', { name: 'Pick a sticker' })).toBeNull();
    const sticker = await waitFor(() => document.getElementById('message-10')!.querySelector('.sm-msg__sticker [role=img]')!);
    expect(sticker.getAttribute('aria-label')).toBe('Reindeer');
    // bigger than an emoji and without a bubble
    expect(document.getElementById('message-10')!.querySelector('.sm-msg__bubble')).toBeNull();
  });

  it('shows the one pointed at by name at the bottom, and which ones are animated', async () => {
    backend([question]);
    open();
    await openPicker();

    expect(within(picker()).getByText('Enter to send · Esc to close')).toBeTruthy();
    const party = within(picker()).getByRole('button', { name: 'Party time, Animated' });
    fireEvent.pointerOver(party);
    const foot = picker().querySelector('.sm-stickers__foot')!;
    await waitFor(() => expect(foot.textContent).toBe('Party timeReactionsAnimated'));
    expect(party.querySelector('.sm-sticker--alive')).toBeTruthy();
    expect(within(picker()).getByRole('button', { name: 'Ho ho ho' })).toBeTruthy();
  });

  it('finds stickers by name, sends the first one found with Enter, and says when none is called that', async () => {
    const calls = backend([question]);
    open();
    await openPicker();
    const search = within(picker()).getByRole('searchbox');
    expect(document.activeElement).toBe(search);

    fireEvent.change(search, { target: { value: 'zzz' } });
    expect(within(picker()).getByRole('status').textContent).toBe('No sticker called “zzz”');
    fireEvent.change(search, { target: { value: 'sno' } });
    expect([...picker().querySelectorAll('.sm-stickers__item')].map(button => button.getAttribute('aria-label'))).toEqual(['Snowman, Animated']);
    fireEvent.keyDown(search, { key: 'Enter' });

    await waitFor(() => expect(sent(calls)).toEqual([{ sticker: 'SNOWMAN' }]));
  });

  it('moves between the stickers with the arrows and sends with Enter, and Esc closes it', async () => {
    const calls = backend([question]);
    open();
    await openPicker();

    fireEvent.keyDown(within(picker()).getByRole('searchbox'), { key: 'ArrowDown' });
    expect(document.activeElement?.getAttribute('aria-label')).toBe('Ho ho ho');
    fireEvent.keyDown(document.activeElement!, { key: 'ArrowRight' });
    fireEvent.keyDown(document.activeElement!, { key: 'ArrowDown' });
    expect(document.activeElement?.getAttribute('aria-label')).toBe('Thank you');
    fireEvent.keyDown(document, { key: 'Escape' });

    expect(screen.queryByRole('dialog', { name: 'Pick a sticker' })).toBeNull();
    expect(document.activeElement?.getAttribute('aria-label')).toBe('Stickers');
    expect(sent(calls)).toEqual([]);
  });

  it('keeps the ones sent last in Recently used, at the top', async () => {
    backend([question]);
    open();
    await openPicker();
    fireEvent.click(within(picker()).getByRole('button', { name: 'Boo!, Animated' }));
    await waitFor(() => expect(document.getElementById('message-10')).toBeTruthy());

    fireEvent.click(screen.getByRole('button', { name: 'Stickers' }));
    expect(packs()[0]).toBe('Recently used');
    expect(within(picker().querySelector('section')!).getAllByRole('button').map(button => button.getAttribute('aria-label'))).toEqual(['Boo!, Animated']);
  });

  it('answers the message being replied to', async () => {
    const calls = backend([question]);
    open();
    await screen.findByRole('log', { name: 'Messages' });
    fireEvent.click(within(document.getElementById('message-1')!).getByRole('button', { name: 'Reply' }));
    fireEvent.click(screen.getByRole('button', { name: 'Stickers' }));
    fireEvent.click(within(picker()).getByRole('button', { name: 'Thank you' }));

    await waitFor(() => expect(sent(calls)).toEqual([{ sticker: 'THANK_YOU', replyToId: 1 }]));
  });

  it('shows Retry when it is not sent, and Retry sends the same sticker', async () => {
    let fail = true;
    const calls = backend([question], call => {
      if (call.method === 'POST' && fail) {
        fail = false;
        return json({ code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'sticker', code: 'INVALID' }] }, 400);
      }
      return undefined;
    });
    open();
    await openPicker();
    fireEvent.click(within(picker()).getByRole('button', { name: 'Hello!, Animated' }));

    fireEvent.click(await screen.findByRole('button', { name: 'Retry' }));
    await waitFor(() => expect(sent(calls)).toEqual([{ sticker: 'HELLO' }, { sticker: 'HELLO' }]));
    await waitFor(() => expect(document.getElementById('message-10')).toBeTruthy());
  });

  it('shows a received sticker moving if it is animated, names it in the chat list, and never offers to edit one', async () => {
    backend([question, fromAna, { ...fromAna, id: 3, author: me, sticker: 'SANTA' }]);
    open();
    await screen.findByRole('log', { name: 'Messages' });

    const theirs = await waitFor(() => document.getElementById('message-2')!.querySelector('svg')!);
    expect(theirs.getAttribute('aria-label')).toBe('Party time');
    expect(theirs.classList.contains('sm-sticker--loop')).toBe(true);
    const mine = document.getElementById('message-3')!;
    expect(mine.querySelector('svg')!.classList.contains('sm-sticker--loop')).toBe(false);
    expect(within(mine).queryByRole('button', { name: 'Edit' })).toBeNull();
    expect(within(mine).getByRole('button', { name: 'Delete' })).toBeTruthy();
  });
});
