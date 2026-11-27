import { cleanup, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { forgetConversations } from '../src/pages/chats/conversations';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { ChatContactData, MessageData } from '../src/types/chat';

vi.mock('../src/api/chatSocket', () => ({
  isChatSocketConnected: () => false, onChatSocketConnection: () => () => undefined, sendTyping: vi.fn(),
  onChatSocketReconnect: () => () => undefined,
  subscribeToChatEvents: () => () => undefined,
}));

configure({ asyncUtilTimeout: 5000 });
vi.setConfig({ testTimeout: 15000 });

const me: ChatContactData = { number: 9, name: 'Test Teacher', role: 'TEACHER' };
const ana: ChatContactData = { number: 50001, name: 'Test Ana', role: 'STUDENT' };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
const PNG = 'image/png';
const message = (changes: Partial<MessageData>): MessageData => ({
  id: 1, chatId: 5, author: ana, text: '', sentAt: new Date().toISOString(), editedAt: null, reply: false, replyTo: null, ...changes,
});
const file = (name: string, size = 2048, type = '') => {
  const made = new File(['x'], name, { type });
  Object.defineProperty(made, 'size', { value: size });
  return made;
};

// the uploads, which go through an XMLHttpRequest to show their progress
interface Upload { url: string; form: FormData; progress: (loaded: number, total: number) => void; answer: (status: number, body: unknown) => void }
let uploads: Upload[] = [];
class FakeXhr {
  status = 0;
  responseText = '';
  withCredentials = false;
  upload: { onprogress?: (event: { lengthComputable: boolean; loaded: number; total: number }) => void } = {};
  onload?: () => void;
  onerror?: () => void;
  private url = '';
  open(_method: string, url: string) { this.url = url; }
  setRequestHeader() { /* the CSRF token */ }
  send(form: FormData) {
    uploads.push({
      url: this.url, form,
      progress: (loaded, total) => this.upload.onprogress?.({ lengthComputable: true, loaded, total }),
      answer: (status, body) => { this.status = status; this.responseText = JSON.stringify(body); this.onload?.(); },
    });
  }
}

function backend(messages: MessageData[] = []) {
  vi.stubGlobal('XMLHttpRequest', FakeXhr);
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:preview'), revokeObjectURL: vi.fn() }));
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async input => {
    const path = new URL(String(input), 'https://test.invalid').pathname;
    if (path === '/api/chats/5/members') return json([me, ana, { number: 50002, name: 'Test Rui', role: 'STUDENT' }]);
    if (path === '/api/chats/5/messages') return json({ messages, reactions: [], hasMore: false, hasNewer: false });
    if (path === '/api/chats/5/read') return new Response(null, { status: 204 });
    if (path === '/api/chats/') return json([{ chatId: 5, name: 'Test Study Group', contact: null, picture: false, pictureUpdatedAt: null, lastMessage: null, unread: 0, assignment: false }]);
    if (path === '/api/chats/unread-count') return json(0);
    if (path === '/api/blocks/') return json([]);
    throw new Error(`Unexpected request: ${path}`);
  }));
}

async function open() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={['/chats/group/5']}><TeacherArea currentUser={{ ...me, institutionalEmail: 'teacher@school.pt', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
  return screen.findByRole('textbox', { name: 'Message Test Study Group' });
}
const pick = (...files: File[]) => fireEvent.change(document.querySelector('input[type=file]')!, { target: { files } });
// found again each time, waiting for the cards to show (CI is slower)
const cards = async () => within(await screen.findByRole('list', { name: 'Files to send' }));
const sendButton = () => screen.getByRole('button', { name: 'Send' }) as HTMLButtonElement;

beforeEach(async () => { await i18n.changeLanguage('en'); uploads = []; });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); forgetConversations(); });

describe('send attachments', () => {
  it('sends picked files with the text, showing their progress until the message comes back', async () => {
    backend();
    await open();

    pick(file('notes.pdf', 1_400_000), file('diagram.png', 180_000, PNG));
    expect((await cards()).getByText('notes.pdf')).toBeTruthy();
    expect((await cards()).getByText('1.3 MB')).toBeTruthy();
    fireEvent.change(screen.getByRole('textbox', { name: 'Message Test Study Group' }), { target: { value: '  for tomorrow ' } });
    fireEvent.click(sendButton());

    await waitFor(() => expect(uploads).toHaveLength(1));
    const [upload] = uploads;
    expect(upload.url).toBe('/api/chats/5/messages');
    expect(upload.form.getAll('files').map(sent => (sent as File).name)).toEqual(['notes.pdf', 'diagram.png']);
    expect(upload.form.get('text')).toBe('for tomorrow');
    expect(screen.queryByRole('list', { name: 'Files to send' })).toBeNull();
    expect(screen.getByText('Sending…')).toBeTruthy();

    upload.answer(201, message({ id: 7, author: me, text: 'for tomorrow', attachments: [
      { id: 1, name: 'notes.pdf', contentType: 'application/pdf', size: 1_400_000 },
      { id: 2, name: 'diagram.png', contentType: PNG, size: 180_000 },
    ] }));
    expect(await screen.findByRole('link', { name: 'Download notes.pdf' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Download notes.pdf' }).getAttribute('href')).toBe('/api/chats/5/messages/7/attachments/1');
    expect(screen.queryByText('Sending…')).toBeNull();
  });

  it('sends files without any text', async () => {
    backend();
    await open();

    pick(file('notes.txt'));
    expect(sendButton().disabled).toBe(false);
    fireEvent.click(sendButton());

    await waitFor(() => expect(uploads).toHaveLength(1));
    expect(uploads[0].form.get('text')).toBeNull();
  });

  it('adds a pasted image as a file, and text pastes as usual', async () => {
    backend();
    const box = await open();

    fireEvent.paste(box, { clipboardData: { files: [file('image.png', 5000, PNG)], getData: () => '' } });

    expect((await cards()).getByText('image.png')).toBeTruthy();
    expect((await screen.findByRole('list', { name: 'Files to send' })).querySelector('img')?.getAttribute('src')).toBe('blob:preview');
    fireEvent.paste(box, { clipboardData: { files: [], getData: () => 'hello' } });
    expect((await cards()).getAllByRole('listitem')).toHaveLength(1);
  });

  it('adds files dropped on the chat', async () => {
    backend();
    await open();
    const thread = document.querySelector('.sm-chat__thread') as HTMLElement;
    const dataTransfer = { types: ['Files'], files: [file('slides.pptx')], dropEffect: 'none' };

    fireEvent.dragEnter(thread, { dataTransfer });
    expect(screen.getByText('Drop to send to Test Study Group')).toBeTruthy();
    fireEvent.drop(thread, { dataTransfer });

    expect((await cards()).getByText('slides.pptx')).toBeTruthy();
    expect(screen.queryByText('Drop to send to Test Study Group')).toBeNull();
  });

  it("says why a file can't be sent before anything goes up, and Send waits until it is taken off", async () => {
    backend();
    await open();

    pick(file('lecture.pdf', 11 * 1024 * 1024), file('project.zip'), file('notes.txt'));

    expect((await cards()).getByText('Over 10 MB')).toBeTruthy();
    expect((await cards()).getByText("This type of file can't be sent")).toBeTruthy();
    expect(sendButton().disabled).toBe(true);
    fireEvent.click((await cards()).getByRole('button', { name: 'Remove lecture.pdf' }));
    fireEvent.click((await cards()).getByRole('button', { name: 'Remove project.zip' }));
    expect(sendButton().disabled).toBe(false);
  });

  it("lets go of an image's preview once its card is taken off", async () => {
    backend();
    await open();

    pick(file('photo.png'));

    await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalled());
    expect(URL.revokeObjectURL).not.toHaveBeenCalled();
    fireEvent.click((await cards()).getByRole('button', { name: 'Remove photo.png' }));
    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview'));
  });

  it('marks the files past 5', async () => {
    backend();
    await open();

    pick(...['a', 'b', 'c', 'd', 'e', 'f'].map(name => file(`${name}.txt`)));

    expect((await cards()).getAllByText('Only 5 files per message')).toHaveLength(1);
    expect(sendButton().disabled).toBe(true);
  });

  it('shows what the backend refused under the box, and Retry sends the same files again', async () => {
    backend();
    await open();
    pick(file('fake.pdf'));
    fireEvent.click(sendButton());
    await waitFor(() => expect(uploads).toHaveLength(1));

    uploads[0].answer(400, { code: 'ATTACHMENT_CONTENT_MISMATCH', params: { name: 'fake.pdf', index: 0 } });

    expect(await screen.findByText("fake.pdf is empty, damaged or isn't the type of file its name says.")).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await waitFor(() => expect(uploads).toHaveLength(2));
    expect((uploads[1].form.get('files') as File).name).toBe('fake.pdf');
  });

  it('shows received images as previews that open bigger, and other files to download', async () => {
    backend([message({ text: 'the diagram', attachments: [
      { id: 3, name: 'diagram.png', contentType: PNG, size: 90_000 },
      { id: 4, name: 'report.docx', contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', size: 48_000 },
    ] })]);
    await open();

    const preview = await screen.findByRole('button', { name: 'Open diagram.png' });
    expect(within(preview).getByRole('img').getAttribute('src')).toBe('/api/chats/5/messages/1/attachments/3');
    expect(screen.getByRole('link', { name: 'Download report.docx' })).toBeTruthy();
    fireEvent.click(preview);

    const dialog = await screen.findByRole('dialog', { name: 'diagram.png' });
    expect(within(dialog).getByRole('img', { name: 'diagram.png' }).getAttribute('src')).toBe('/api/chats/5/messages/1/attachments/3');
  });
});
