import { act, cleanup, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { forgetConversations } from '../src/pages/chats/conversations';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { ChatContactData, ChatEvent, MessageData, ReactionData } from '../src/types/chat';

// every listener of a destination gets what the test pushes
const live = vi.hoisted(() => ({ listeners: new Map<string, Set<(event: unknown) => void>>() }));
vi.mock('../src/api/chatSocket', () => ({
  subscribeToChatEvents: (destination: string, onEvent: (event: unknown) => void) => {
    const set = live.listeners.get(destination) ?? new Set();
    set.add(onEvent);
    live.listeners.set(destination, set);
    return () => set.delete(onEvent);
  },
}));
// the real picker draws every emoji; a few buttons are enough to pick one
vi.mock('emoji-picker-react', () => ({
  default: ({ onEmojiClick }: { onEmojiClick: (emoji: { emoji: string }) => void }) => (
    <div>{['👍', '🎉'].map(emoji => <button key={emoji} type="button" onClick={() => onEmojiClick({ emoji })}>{emoji}</button>)}</div>
  ),
}));

configure({ asyncUtilTimeout: 5000 });
vi.setConfig({ testTimeout: 15000 });

const me: ChatContactData = { number: 9, name: 'Test Teacher', role: 'TEACHER' };
const ana: ChatContactData = { number: 50001, name: 'Test Ana', role: 'STUDENT' };
const CHAT = 7;
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

const question: MessageData = { id: 1, chatId: CHAT, author: ana, text: 'Can you check my report?', sentAt: '2026-11-21T09:00:00', editedAt: null, reply: false, replyTo: null };
const reaction = (user: ChatContactData, emoji: string | null): ReactionData => ({ chatId: CHAT, messageId: 1, user, emoji });

interface Call { method: string; path: string; body?: unknown }

// the DM with Ana, with one message and its reactions. `answer` takes reacting and taking it off
function backend(reactions: ReactionData[], answer: (call: Call) => Response | undefined = () => undefined) {
  const calls: Call[] = [];
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    const call = { method: init?.method ?? 'GET', path: url.pathname, body: init?.body ? JSON.parse(String(init.body)) : undefined };
    calls.push(call);
    const answered = answer(call);
    if (answered) return answered;
    if (call.path === `/api/chats/${CHAT}/messages/1/reaction`) {
      return call.method === 'PUT' ? json(reaction(me, (call.body as { emoji: string }).emoji)) : new Response(null, { status: 204 });
    }
    if (call.path === `/api/chats/${CHAT}/messages`) return json({ messages: [question], reactions, hasMore: false, hasNewer: false });
    if (call.path === `/api/chats/direct/${ana.number}`) return json({ chatId: CHAT, contact: ana, blocked: false });
    if (call.path === `/api/chats/${CHAT}/read`) return new Response(null, { status: 204 });
    if (call.path === '/api/chats/') return json([]);
    if (call.path === '/api/chats/unread-count') return json(0);
    throw new Error(`Unexpected request: ${call.method} ${call.path}`);
  }));
  return calls;
}

function open() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={[`/chats/direct/${ana.number}`]}><TeacherArea currentUser={{ ...me, institutionalEmail: 'teacher@school.pt', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

const row = () => document.getElementById('message-1')!;
// the pills under the message, as "emoji count"
const pills = () => [...row().querySelectorAll('.sm-reaction:not(.sm-reaction--add)')].map(pill => pill.textContent);
const pill = (emoji: string) => [...row().querySelectorAll<HTMLButtonElement>('.sm-reaction')].find(button => button.textContent?.startsWith(emoji))!;
const reactions = (calls: Call[]) => calls.filter(call => call.path.endsWith('/reaction')).map(call => `${call.method}${call.body ? ` ${(call.body as { emoji: string }).emoji}` : ''}`);
// MESSAGE_REACTED goes to each member's own queue for the chat
const pushReaction = (payload: ReactionData) => act(() => {
  live.listeners.get(`/user/queue/chats/${CHAT}`)?.forEach(listener => listener({ type: 'MESSAGE_REACTED', payload } satisfies ChatEvent));
});

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); forgetConversations(); live.listeners.clear(); });

describe('react to a message', () => {
  it('shows the same emoji from several people as one pill with the count, yours highlighted, and who on hover', async () => {
    backend([reaction(ana, '👍'), reaction(me, '👍'), { ...reaction(ana, '🎉'), messageId: 99 }]);
    open();
    await screen.findByRole('log', { name: 'Messages' });

    await waitFor(() => expect(pills()).toEqual(['👍2']));
    expect(pill('👍').title).toBe('You and Test Ana');
    expect(pill('👍').getAttribute('aria-pressed')).toBe('true');
    expect(pill('👍').getAttribute('aria-label')).toBe('👍 2, from You and Test Ana. Take yours off');
  });

  it('adds a reaction from the picker, and picking another one replaces it', async () => {
    const calls = backend([]);
    open();
    await screen.findByRole('log', { name: 'Messages' });

    fireEvent.click(within(row()).getByRole('button', { name: 'Add reaction' }));
    fireEvent.click(await within(screen.getByRole('dialog', { name: 'Pick a reaction' })).findByRole('button', { name: '👍' }));

    await waitFor(() => expect(pills()).toEqual(['👍1']));
    expect(screen.queryByRole('dialog', { name: 'Pick a reaction' })).toBeNull();

    fireEvent.click(within(row()).getAllByRole('button', { name: 'Add reaction' })[0]);
    fireEvent.click(await screen.findByRole('button', { name: '🎉' }));

    // one each: the new one takes the old one's place
    await waitFor(() => expect(pills()).toEqual(['🎉1']));
    expect(reactions(calls)).toEqual(['PUT 👍', 'PUT 🎉']);
  });

  it("clicking someone else's pill makes it yours, and clicking yours takes it off", async () => {
    const calls = backend([reaction(ana, '🎉'), reaction(me, '👍')]);
    open();
    await waitFor(() => expect(pills()).toEqual(['🎉1', '👍1']));

    fireEvent.click(pill('🎉'));
    await waitFor(() => expect(pills()).toEqual(['🎉2']));

    fireEvent.click(pill('🎉'));
    // the last reaction of an emoji gone: its pill goes, Ana's stays
    await waitFor(() => expect(pills()).toEqual(['🎉1']));
    expect(reactions(calls)).toEqual(['PUT 🎉', 'DELETE']);
  });

  it('removes the pill when the last reaction with that emoji is taken off', async () => {
    backend([reaction(me, '👍')]);
    open();
    await waitFor(() => expect(pills()).toEqual(['👍1']));

    fireEvent.click(pill('👍'));

    await waitFor(() => expect(row().querySelector('.sm-reactions')).toBeNull());
  });

  it('reacts with a heart on a double click, and on a double tap', async () => {
    const calls = backend([]);
    open();
    await screen.findByRole('log', { name: 'Messages' });
    const bubble = row().querySelector('.sm-msg__line')!;

    fireEvent.doubleClick(bubble);
    await waitFor(() => expect(pills()).toEqual(['❤️1']));

    // a heart again changes nothing, so nothing is sent
    fireEvent.pointerUp(bubble, { pointerType: 'touch' });
    fireEvent.pointerUp(bubble, { pointerType: 'touch' });
    expect(reactions(calls)).toEqual(['PUT ❤️']);
  });

  it('does not select the word under a double click', async () => {
    backend([]);
    open();
    await screen.findByRole('log', { name: 'Messages' });
    const line = row().querySelector('.sm-msg__line')!;

    // the first press of a double click selects as usual, the second one doesn't
    expect(fireEvent.mouseDown(line, { detail: 1 })).toBe(true);
    expect(fireEvent.mouseDown(line, { detail: 2 })).toBe(false);
  });

  it('reacts with a heart on two quick taps on a touch screen', async () => {
    const calls = backend([]);
    open();
    await screen.findByRole('log', { name: 'Messages' });
    const bubble = row().querySelector('.sm-msg__line')!;

    fireEvent.pointerUp(bubble, { pointerType: 'touch' });
    fireEvent.pointerUp(bubble, { pointerType: 'touch' });

    await waitFor(() => expect(reactions(calls)).toEqual(['PUT ❤️']));
  });

  it('updates live when someone else reacts, changes theirs or takes it off', async () => {
    backend([]);
    open();
    await screen.findByRole('log', { name: 'Messages' });

    pushReaction(reaction(ana, '👍'));
    await waitFor(() => expect(pills()).toEqual(['👍1']));
    pushReaction(reaction(ana, '🎉'));
    await waitFor(() => expect(pills()).toEqual(['🎉1']));
    pushReaction(reaction(ana, null));
    await waitFor(() => expect(row().querySelector('.sm-reactions')).toBeNull());
  });

  it('shows a toast when the backend refuses the reaction', async () => {
    backend([], call => call.method === 'PUT' ? json({ code: 'INVALID_EMOJI' }, 400) : undefined);
    open();
    await screen.findByRole('log', { name: 'Messages' });

    fireEvent.doubleClick(row().querySelector('.sm-msg__line')!);

    expect(await screen.findByText('React with a single emoji.')).toBeTruthy();
    expect(row().querySelector('.sm-reactions')).toBeNull();
  });
});
