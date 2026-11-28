import { cleanup, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { StrictMode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { forgetConversations } from '../src/pages/chats/conversations';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import pt from '../src/i18n/pt.json';
import type { ChatContactData, MessageData } from '../src/types/chat';

vi.mock('../src/api/chatSocket', () => ({ onChatSocketReconnect: () => () => undefined, isChatSocketConnected: () => false,
  onChatSocketConnection: () => () => undefined, sendTyping: vi.fn(), subscribeToChatEvents: () => () => undefined }));
configure({ asyncUtilTimeout: 5000 });
vi.setConfig({ testTimeout: 15000 });

const me: ChatContactData = { number: 9, name: 'Test Teacher', role: 'TEACHER' };
const ana: ChatContactData = { number: 50001, name: 'Test Ana', role: 'STUDENT' };
const CHAT = 7;
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

const question: MessageData = { id: 1, chatId: CHAT, author: ana, text: 'Ready for the exam?', sentAt: '2026-12-10T09:00:00', editedAt: null, reply: false, replyTo: null };
const gifMessage = (id: number, gifId: string, author = ana): MessageData => ({ ...question, id, author, text: '', gifId, sentAt: `2026-12-10T09:0${id}:00` });

// a GIF the way GIPHY answers it, the sizes as text
const giphy = (id: string, title = `${id} gif`) => ({ id, title, images: { fixed_width: { url: `https://media.giphy.test/${id}.gif`, width: '200', height: '150' } } });

interface Call { method: string; path: string; params: URLSearchParams; body?: unknown }

// our backend for the DM with Ana, and GIPHY. Sending answers with the message saved, unless `answer` says otherwise
function backend(messages: MessageData[], answer: (call: Call) => Response | undefined = () => undefined) {
  const calls: Call[] = [];
  let next = 10;
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    const call = { method: init?.method ?? 'GET', path: url.host === 'api.giphy.com' ? `giphy:${url.pathname}` : url.pathname, params: url.searchParams,
      body: init?.body ? JSON.parse(String(init.body)) : undefined };
    calls.push(call);
    const answered = answer(call);
    if (answered) return answered;
    if (call.path === 'giphy:/v1/gifs/trending') return json({ data: [giphy('trend1'), giphy('trend2')] });
    if (call.path === 'giphy:/v1/gifs/search') return json({ data: [giphy('found1', 'Good luck'), giphy('found2')] });
    if (call.path === 'giphy:/v1/gifs') return json({ data: url.searchParams.get('ids')!.split(',').map(id => giphy(id)) });
    if (call.path === `/api/chats/direct/${ana.number}/messages` && call.method === 'POST') {
      const { gifId, text } = call.body as { gifId?: string; text?: string };
      return json({ ...question, id: next++, author: me, text: text ?? '', gifId: gifId ?? null, sentAt: '2026-12-10T10:00:00' } satisfies MessageData, 201);
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

// in StrictMode like the app, which runs each effect twice in development
function open() {
  render(<StrictMode><StudyMatchProvider><MemoryRouter initialEntries={[`/chats/direct/${ana.number}`]}><TeacherArea currentUser={{ ...me, institutionalEmail: 'teacher@school.pt', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider></StrictMode>);
}

const giphyCalls = (calls: Call[], path: string) => calls.filter(call => call.path === `giphy:${path}`);
const sent = (calls: Call[]) => calls.filter(call => call.method === 'POST' && call.path === `/api/chats/direct/${ana.number}/messages`).map(call => call.body);
const panel = () => screen.getByRole('dialog', { name: 'Pick a GIF' });
async function openPanel() {
  await screen.findByRole('log', { name: 'Messages' });
  fireEvent.click(screen.getByRole('button', { name: 'GIFs' }));
  return panel();
}
const search = (text: string) => fireEvent.change(within(panel()).getByPlaceholderText('Search GIPHY'), { target: { value: text } });
const gifIn = (id: number) => document.getElementById(`message-${id}`)!.querySelector('.sm-msg__gif')!;
// the GIF's picture once GIPHY has answered
const imageIn = (id: number) => waitFor(() => {
  const image = gifIn(id).querySelector('img');
  if (!image) throw new Error(`message ${id} has no GIF yet`);
  return image;
});

beforeEach(async () => {
  await i18n.changeLanguage('en');
  vi.stubEnv('VITE_GIPHY_API_KEY', 'test-key');
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.unstubAllEnvs(); vi.restoreAllMocks(); forgetConversations(); });

describe('send a GIF', () => {
  it('opens on the trending ones, with Powered by GIPHY, and searches once typing stops', async () => {
    const calls = backend([question]);
    open();
    await openPanel();

    expect(await within(panel()).findByRole('button', { name: 'trend1 gif' })).toBeTruthy();
    expect(within(panel()).getByText('Powered by GIPHY')).toBeTruthy();
    expect(giphyCalls(calls, '/v1/gifs/trending')[0].params.get('api_key')).toBe('test-key');
    // typing a bit slower than the computer: each pause is shorter than the wait before searching
    for (const text of ['g', 'go', 'good', 'good luck']) {
      search(text);
      await new Promise(resolve => setTimeout(resolve, 150));
    }

    expect(await within(panel()).findByRole('button', { name: 'Good luck' })).toBeTruthy();
    expect(giphyCalls(calls, '/v1/gifs/search').map(call => call.params.get('q'))).toEqual(['good luck']);
  });

  it('sends the GIF picked on its own, and shows it without asking GIPHY for it again', async () => {
    const calls = backend([question]);
    open();
    await openPanel();
    search('good luck');
    fireEvent.click(await within(panel()).findByRole('button', { name: 'Good luck' }));

    await waitFor(() => expect(sent(calls)).toEqual([{ gifId: 'found1' }]));
    expect(screen.queryByRole('dialog', { name: 'Pick a GIF' })).toBeNull();
    const gif = await imageIn(10);
    expect(gif.getAttribute('src')).toBe('https://media.giphy.test/found1.gif');
    expect(gif.getAttribute('alt')).toBe('Good luck');
    expect(document.getElementById('message-10')!.querySelector('.sm-msg__bubble')).toBeNull();
    expect(giphyCalls(calls, '/v1/gifs')).toHaveLength(0);
    // a GIF has no text to edit
    fireEvent.keyDown(screen.getByRole('textbox', { name: 'Message Test Ana' }), { key: 'ArrowUp' });
    expect(screen.queryByRole('textbox', { name: 'Edit message' })).toBeNull();
  });

  it('says when the search finds nothing', async () => {
    backend([question], call => call.path === 'giphy:/v1/gifs/search' ? json({ data: [] }) : undefined);
    open();
    await openPanel();
    search('zzzz');

    expect(await within(panel()).findByText('No GIFs found. Try other words.')).toBeTruthy();
  });

  it("says when GIPHY is down, doesn't ask again by itself, and Try again does", async () => {
    let down = true;
    const calls = backend([question], call => call.path === 'giphy:/v1/gifs/trending' && down ? new Response('', { status: 503 }) : undefined);
    open();
    await openPanel();

    expect(await within(panel()).findByText("GIPHY isn't answering right now. You can still send text.")).toBeTruthy();
    await new Promise(resolve => setTimeout(resolve, 600));
    expect(giphyCalls(calls, '/v1/gifs/trending')).toHaveLength(1);
    down = false;
    fireEvent.click(within(panel()).getByRole('button', { name: 'Try again' }));
    expect(await within(panel()).findByRole('button', { name: 'trend1 gif' })).toBeTruthy();
    expect(giphyCalls(calls, '/v1/gifs/trending')).toHaveLength(2);
  });

  it('says to try later when the calls ran out, and text can still be sent', async () => {
    const calls = backend([question], call => call.path.startsWith('giphy:') ? new Response('', { status: 429 }) : undefined);
    open();
    await openPanel();

    expect(await within(panel()).findByText('Too many GIF searches for now. Try again later, you can still send text.')).toBeTruthy();
    fireEvent.change(screen.getByRole('textbox', { name: 'Message Test Ana' }), { target: { value: 'Good luck!' } });
    fireEvent.click(await screen.findByRole('button', { name: 'Send' }));
    await waitFor(() => expect(sent(calls)).toEqual([{ text: 'Good luck!' }]));
  });

  it("says GIFs aren't set up when there's no key, without calling GIPHY", async () => {
    vi.stubEnv('VITE_GIPHY_API_KEY', '');
    const calls = backend([question, gifMessage(2, 'old1')]);
    open();
    await openPanel();

    expect(await within(panel()).findByText("GIFs aren't set up here. You can still send text.")).toBeTruthy();
    expect(within(gifIn(2)).getByText('GIF not available')).toBeTruthy();
    expect(calls.filter(call => call.path.startsWith('giphy:'))).toHaveLength(0);
  });

  it("treats an answer that isn't what GIPHY should send as GIPHY being down", async () => {
    backend([question], call => call.path === 'giphy:/v1/gifs/trending' ? json({ meta: { status: 200 } }) : undefined);
    open();
    await openPanel();

    expect(await within(panel()).findByText("GIPHY isn't answering right now. You can still send text.")).toBeTruthy();
  });

  it("asks for the chat's GIFs together, and the ones GIPHY leaves out say they aren't available", async () => {
    const calls = backend([question, gifMessage(2, 'old1'), gifMessage(3, 'gone'), gifMessage(4, 'old1')],
      call => call.path === 'giphy:/v1/gifs' ? json({ data: [giphy('old1')] }) : undefined);
    open();

    const first = await imageIn(2);
    expect(first.getAttribute('src')).toBe('https://media.giphy.test/old1.gif');
    expect(gifIn(4).querySelector('img')).toBeTruthy();
    expect(within(gifIn(3)).getByText('GIF not available')).toBeTruthy();
    expect(giphyCalls(calls, '/v1/gifs').map(call => call.params.get('ids'))).toEqual(['old1,gone']);
  });

  it("shows GIF not available in the chat when the calls ran out, and the rest of the chat works", async () => {
    const calls = backend([question, gifMessage(2, 'old1')], call => call.path === 'giphy:/v1/gifs' ? new Response('', { status: 429 }) : undefined);
    open();

    expect(await waitFor(() => within(gifIn(2)).getByText('GIF not available'))).toBeTruthy();
    expect(screen.getByText('Ready for the exam?')).toBeTruthy();
    fireEvent.change(screen.getByRole('textbox', { name: 'Message Test Ana' }), { target: { value: 'Hi' } });
    fireEvent.click(await screen.findByRole('button', { name: 'Send' }));
    await waitFor(() => expect(sent(calls)).toEqual([{ text: 'Hi' }]));
    // asked once while the chat is open, even after a new message
    expect(giphyCalls(calls, '/v1/gifs')).toHaveLength(1);
  });

  it("shows GIF not available when the GIF itself doesn't load", async () => {
    backend([question, gifMessage(2, 'old1')]);
    open();

    const gif = await imageIn(2);
    fireEvent.error(gif);
    expect(await waitFor(() => within(gifIn(2)).getByText('GIF not available'))).toBeTruthy();
  });

  it('has its text in Portuguese too', () => {
    for (const key of ['open', 'pick', 'search', 'poweredBy', 'none', 'down', 'limit', 'noKey', 'retry', 'unavailable'] as const) {
      expect(pt.chats.gifs[key]).toBeTruthy();
    }
  });
});
