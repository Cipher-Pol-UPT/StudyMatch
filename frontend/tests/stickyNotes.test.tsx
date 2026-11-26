import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createNote, deleteNote, getNotes, updateNote } from '../src/api/notes';
import { StickyNotes } from '../src/components/notes/StickyNotes';
import i18n from '../src/i18n';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { NoteData } from '../src/types/notes';

vi.mock('../src/api/notes', () => ({
  getNotes: vi.fn(),
  createNote: vi.fn(),
  updateNote: vi.fn(),
  deleteNote: vi.fn(),
}));

const note = (id: number, text: string, changes: Partial<NoteData> = {}): NoteData => ({
  id, text, colour: 'YELLOW', x: 10, y: 10, createdAt: `2026-11-2${id}T10:00:00`, updatedAt: `2026-11-2${id}T10:00:00`, ...changes,
});

// a phone or a desktop, by what the media query answers
function screenWidth(phone: boolean) {
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: phone, addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn() })));
}

function renderNotes() {
  render(<StudyMatchProvider><StickyNotes /></StudyMatchProvider>);
}

const board = () => document.querySelector('.sticky-notes__board') as HTMLElement;
const spotOf = (text: string) => screen.getByText(text).closest('.sticky-notes__spot') as HTMLElement;

beforeEach(async () => {
  await i18n.changeLanguage('en');
  screenWidth(false);
  vi.mocked(getNotes).mockResolvedValue([note(1, 'Study recursion'), note(2, 'Buy pens', { x: 50, colour: 'PINK' })]);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.mocked(createNote).mockReset();
  vi.mocked(updateNote).mockReset();
  vi.mocked(deleteNote).mockReset();
});

describe('sticky notes', () => {
  it('shows the notes on the board, the last one changed on top', async () => {
    vi.mocked(getNotes).mockResolvedValue([note(2, 'Newer', { updatedAt: '2026-11-25T10:00:00' }), note(1, 'Older', { updatedAt: '2026-11-24T10:00:00' })]);
    renderNotes();

    await screen.findByText('Older');

    const texts = [...board().querySelectorAll('.sticky-notes__spot')].map(spot => spot.textContent);
    expect(texts[0]).toContain('Older');
    expect(texts[1]).toContain('Newer');
  });

  it('says so when there are no notes yet', async () => {
    vi.mocked(getNotes).mockResolvedValue([]);
    renderNotes();

    expect(await screen.findByText('No notes yet. Add one for anything you want to keep in mind.')).toBeTruthy();
  });

  it('adds a note with the text and colour picked, in a free spot', async () => {
    vi.mocked(createNote).mockImplementation(async request => note(3, request.text, { colour: request.colour, x: request.x, y: request.y }));
    renderNotes();
    await screen.findByText('Study recursion');

    fireEvent.change(screen.getByRole('textbox', { name: 'New note' }), { target: { value: 'Exam on the 12th' } });
    fireEvent.click(screen.getByRole('radio', { name: 'Green' }));
    fireEvent.click(screen.getByRole('button', { name: 'Stick' }));

    expect(await screen.findByText('Exam on the 12th')).toBeTruthy();
    const sent = vi.mocked(createNote).mock.calls[0][0];
    expect(sent.text).toBe('Exam on the 12th');
    expect(sent.colour).toBe('GREEN');
    expect([sent.x, sent.y]).not.toEqual([10, 10]);
    expect((screen.getByRole('textbox', { name: 'New note' }) as HTMLInputElement).value).toBe('');
  });

  it('shows NOTE_LIMIT_REACHED under the composer', async () => {
    vi.mocked(createNote).mockRejectedValue({ code: 'NOTE_LIMIT_REACHED', params: { max: 20 } });
    renderNotes();
    await screen.findByText('Study recursion');

    fireEvent.click(screen.getByRole('button', { name: 'Stick' }));

    expect((await screen.findByRole('alert')).textContent).toBe('You can have up to 20 notes. Delete one to add another.');
  });

  it('turns the composer off at 20 notes', async () => {
    vi.mocked(getNotes).mockResolvedValue(Array.from({ length: 20 }, (_, i) => note(i + 1, `Note ${i + 1}`)));
    renderNotes();
    await screen.findByText('Note 20');

    expect((screen.getByRole('button', { name: 'Stick' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('edits a note in place and saves when you click away', async () => {
    vi.mocked(updateNote).mockResolvedValue(note(1, 'Study recursion and trees'));
    renderNotes();
    fireEvent.click(await screen.findByRole('button', { name: 'Open note: Study recursion' }));

    const box = screen.getByRole('textbox', { name: 'Note text' });
    expect(document.activeElement).toBe(box);
    fireEvent.change(box, { target: { value: 'Study recursion and trees' } });
    fireEvent.blur(box);

    expect(await screen.findByText('Study recursion and trees')).toBeTruthy();
    expect(updateNote).toHaveBeenCalledWith(1, { text: 'Study recursion and trees' });
  });

  it('keeps the text open with the error when it is too long', async () => {
    vi.mocked(updateNote).mockRejectedValue({ code: 'NOTE_TOO_LONG', params: { max: 500 } });
    renderNotes();
    fireEvent.click(await screen.findByRole('button', { name: 'Open note: Study recursion' }));
    const box = screen.getByRole('textbox', { name: 'Note text' });
    fireEvent.change(box, { target: { value: 'a'.repeat(501) } });

    fireEvent.blur(box);

    expect((await screen.findByRole('alert')).textContent).toBe('A note can have up to 500 characters.');
    expect((screen.getByRole('textbox', { name: 'Note text' }) as HTMLTextAreaElement).value).toBe('a'.repeat(501));
  });

  it('changes the colour from the note while it is open', async () => {
    vi.mocked(updateNote).mockResolvedValue(note(1, 'Study recursion', { colour: 'BLUE' }));
    renderNotes();
    fireEvent.click(await screen.findByRole('button', { name: 'Open note: Study recursion' }));

    fireEvent.click(within(spotOf('Study recursion')).getByRole('radio', { name: 'Blue' }));

    await waitFor(() => expect(updateNote).toHaveBeenCalledWith(1, { colour: 'BLUE' }));
  });

  it('deletes a note straight away', async () => {
    vi.mocked(deleteNote).mockResolvedValue(undefined);
    renderNotes();
    await screen.findByText('Study recursion');

    fireEvent.click(within(spotOf('Study recursion')).getByRole('button', { name: 'Remove note' }));

    await waitFor(() => expect(screen.queryByText('Study recursion')).toBeNull());
    expect(deleteNote).toHaveBeenCalledWith(1);
  });

  it('takes a note off the board when the backend no longer has it', async () => {
    vi.mocked(updateNote).mockRejectedValue({ code: 'NOT_FOUND' });
    renderNotes();
    fireEvent.click(await screen.findByRole('button', { name: 'Open note: Study recursion' }));
    const box = screen.getByRole('textbox', { name: 'Note text' });
    fireEvent.change(box, { target: { value: 'changed' } });

    fireEvent.blur(box);

    await waitFor(() => expect(screen.queryByText('Study recursion')).toBeNull());
  });

  describe('dragging', () => {
    beforeEach(() => {
      // jsdom has no real pointer to capture
      Object.defineProperty(HTMLElement.prototype, 'setPointerCapture', { value: vi.fn(), configurable: true });
      vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({ x: 0, y: 0, top: 0, left: 0, right: 1000, bottom: 500, width: 1000, height: 500, toJSON: () => ({}) });
    });

    const dragBy = (spot: HTMLElement, dx: number, dy: number) => {
      fireEvent.pointerDown(spot, { button: 0, clientX: 100, clientY: 50, pointerId: 1 });
      fireEvent.pointerMove(spot, { clientX: 100 + dx, clientY: 50 + dy, pointerId: 1, buttons: 1 });
      fireEvent.pointerUp(spot, { clientX: 100 + dx, clientY: 50 + dy, pointerId: 1 });
    };

    it('saves the new position once, on drop', async () => {
      vi.mocked(updateNote).mockImplementation(async (_, changes) => note(1, 'Study recursion', changes));
      renderNotes();
      await screen.findByText('Study recursion');

      await act(async () => dragBy(spotOf('Study recursion'), 200, 100));

      expect(updateNote).toHaveBeenCalledTimes(1);
      expect(updateNote).toHaveBeenCalledWith(1, { x: 30, y: 30 });
    });

    it('puts the note back where it was when the drop fails to save', async () => {
      vi.mocked(updateNote).mockRejectedValue(new Error('offline'));
      renderNotes();
      await screen.findByText('Study recursion');

      await act(async () => dragBy(spotOf('Study recursion'), 200, 100));

      expect((await screen.findByRole('alert')).textContent).toBe("Couldn't move the note. It's back where it was.");
      expect(spotOf('Study recursion').style.left).toContain('min(10%');
      expect(spotOf('Study recursion').style.top).toContain('min(10%');
    });

    it("doesn't drag a note when the button was let go somewhere else", async () => {
      renderNotes();
      await screen.findByText('Study recursion');
      const spot = spotOf('Study recursion');

      // pressed near the edge, flicked off before it counted as a drag, let go outside
      fireEvent.pointerDown(spot, { button: 0, clientX: 100, clientY: 50, pointerId: 1 });
      fireEvent.pointerMove(spot, { clientX: 300, clientY: 150, pointerId: 1, buttons: 0 });
      fireEvent.pointerUp(spot, { clientX: 300, clientY: 150, pointerId: 1 });

      expect(spot.classList.contains('sticky-notes__spot--dragging')).toBe(false);
      expect(updateNote).not.toHaveBeenCalled();
    });

    it("keeps the second drop's place when the first drop's answer comes after it", async () => {
      let first: (saved: NoteData) => void = () => {};
      vi.mocked(updateNote)
        .mockReturnValueOnce(new Promise(resolve => { first = resolve; }))
        .mockResolvedValueOnce(note(1, 'Study recursion', { x: 40, y: 30 }));
      renderNotes();
      await screen.findByText('Study recursion');

      await act(async () => dragBy(spotOf('Study recursion'), 200, 100));
      await act(async () => dragBy(spotOf('Study recursion'), 100, 0));
      await act(async () => first(note(1, 'Study recursion', { x: 30, y: 30 })));

      expect(vi.mocked(updateNote).mock.calls.map(call => call[1])).toEqual([{ x: 30, y: 30 }, { x: 40, y: 30 }]);
      expect(spotOf('Study recursion').style.left).toContain('min(40%');
    });

    it('only puts the position back when a drop fails, a text saved meanwhile stays', async () => {
      let failMove: (error: unknown) => void = () => {};
      vi.mocked(updateNote).mockImplementation((_, changes) => 'x' in changes
        ? new Promise((_resolve, reject) => { failMove = reject; })
        : Promise.resolve(note(1, 'Study recursion and trees')));
      renderNotes();
      await screen.findByText('Study recursion');
      await act(async () => dragBy(spotOf('Study recursion'), 200, 100));

      fireEvent.click(screen.getByRole('button', { name: 'Open note: Study recursion' }));
      const box = screen.getByRole('textbox', { name: 'Note text' });
      fireEvent.change(box, { target: { value: 'Study recursion and trees' } });
      fireEvent.blur(box);
      expect(await screen.findByText('Study recursion and trees')).toBeTruthy();
      await act(async () => failMove(new Error('offline')));

      expect(screen.getByText('Study recursion and trees')).toBeTruthy();
      expect(spotOf('Study recursion and trees').style.left).toContain('min(10%');
    });

    it('goes back to where it was saved when two drops both fail', async () => {
      const fails: ((error: unknown) => void)[] = [];
      vi.mocked(updateNote).mockImplementation(() => new Promise((_resolve, reject) => { fails.push(reject); }));
      renderNotes();
      await screen.findByText('Study recursion');

      await act(async () => dragBy(spotOf('Study recursion'), 200, 100));
      await act(async () => dragBy(spotOf('Study recursion'), 100, 0));
      await act(async () => fails[0](new Error('offline')));
      await act(async () => fails[1](new Error('offline')));

      expect(spotOf('Study recursion').style.left).toContain('min(10%');
      expect(spotOf('Study recursion').style.top).toContain('min(10%');
    });

    it("keeps a pending drop's place when a text saves meanwhile", async () => {
      let saveMove: (saved: NoteData) => void = () => {};
      vi.mocked(updateNote).mockImplementation((_, changes) => 'x' in changes
        ? new Promise(resolve => { saveMove = resolve; })
        // the text's answer still has the place from before the drop
        : Promise.resolve(note(1, 'Study recursion and trees', { x: 10, y: 10 })));
      renderNotes();
      await screen.findByText('Study recursion');
      await act(async () => dragBy(spotOf('Study recursion'), 200, 100));

      fireEvent.click(screen.getByRole('button', { name: 'Open note: Study recursion' }));
      const box = screen.getByRole('textbox', { name: 'Note text' });
      fireEvent.change(box, { target: { value: 'Study recursion and trees' } });
      fireEvent.blur(box);
      expect(await screen.findByText('Study recursion and trees')).toBeTruthy();

      expect(spotOf('Study recursion and trees').style.left).toContain('min(30%');
      await act(async () => saveMove(note(1, 'Study recursion and trees', { x: 30, y: 30 })));
      expect(spotOf('Study recursion and trees').style.left).toContain('min(30%');
    });

    it("keeps an unsaved text's error when the note is moved", async () => {
      vi.mocked(updateNote).mockImplementation(async (_, changes) => {
        if ('text' in changes) throw { code: 'NOTE_TOO_LONG', params: { max: 500 } };
        return note(1, 'Study recursion', changes);
      });
      renderNotes();
      fireEvent.click(await screen.findByRole('button', { name: 'Open note: Study recursion' }));
      const box = screen.getByRole('textbox', { name: 'Note text' });
      fireEvent.change(box, { target: { value: 'a'.repeat(501) } });
      fireEvent.blur(box);
      await screen.findByRole('alert');
      // another note opens, so the first can be dragged
      fireEvent.click(screen.getByRole('button', { name: 'Open note: Buy pens' }));

      await act(async () => dragBy(spotOf('a'.repeat(501)), 200, 100));

      expect(screen.getByRole('alert').textContent).toBe('A note can have up to 500 characters.');
    });

    it('opens the note instead when it barely moved', async () => {
      renderNotes();
      await screen.findByText('Study recursion');

      fireEvent.pointerDown(spotOf('Study recursion'), { button: 0, clientX: 100, clientY: 50 });
      fireEvent.pointerUp(spotOf('Study recursion'), { clientX: 101, clientY: 50 });
      fireEvent.click(screen.getByRole('button', { name: 'Open note: Study recursion' }));

      expect(screen.getByRole('textbox', { name: 'Note text' })).toBeTruthy();
      expect(updateNote).not.toHaveBeenCalled();
    });
  });

  it('shows the same notes as a notebook on a phone, newest first, with no board', async () => {
    screenWidth(true);
    renderNotes();

    const notebook = await screen.findByRole('list', { name: 'Notes' });

    const texts = within(notebook).getAllByRole('listitem').map(item => item.textContent);
    expect(texts[0]).toContain('Buy pens');
    expect(texts[1]).toContain('Study recursion');
    expect(board()).toBeNull();
  });

  it('keeps a text that failed to save on its note when another note is opened first', async () => {
    let refuse: (error: unknown) => void = () => {};
    vi.mocked(updateNote).mockReturnValue(new Promise((_resolve, reject) => { refuse = reject; }));
    renderNotes();
    fireEvent.click(await screen.findByRole('button', { name: 'Open note: Study recursion' }));
    const box = screen.getByRole('textbox', { name: 'Note text' });
    fireEvent.change(box, { target: { value: 'a'.repeat(501) } });

    fireEvent.blur(box);
    fireEvent.click(screen.getByRole('button', { name: 'Open note: Buy pens' }));
    await act(async () => refuse({ code: 'NOTE_TOO_LONG', params: { max: 500 } }));

    expect((await screen.findByRole('alert')).textContent).toBe('A note can have up to 500 characters.');
    expect((screen.getByRole('textbox', { name: 'Note text' }) as HTMLTextAreaElement).value).toBe('Buy pens');
    fireEvent.click(screen.getByRole('button', { name: `Open note: ${'a'.repeat(501)}` }));
    expect((screen.getByRole('textbox', { name: 'Note text' }) as HTMLTextAreaElement).value).toBe('a'.repeat(501));
  });

  it('keeps what you type while the text saves, and the answer leaves the note open', async () => {
    let answer: (saved: NoteData) => void = () => {};
    vi.mocked(updateNote).mockReturnValue(new Promise(resolve => { answer = resolve; }));
    renderNotes();
    fireEvent.click(await screen.findByRole('button', { name: 'Open note: Study recursion' }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Note text' }), { target: { value: 'Study recursion and trees' } });
    fireEvent.blur(screen.getByRole('textbox', { name: 'Note text' }));

    // back in the box before the answer comes
    const box = screen.getByRole('textbox', { name: 'Note text' }) as HTMLTextAreaElement;
    box.focus();
    expect(box.value).toBe('Study recursion and trees');
    fireEvent.change(box, { target: { value: 'Study recursion and trees and graphs' } });
    await act(async () => answer(note(1, 'Study recursion and trees')));

    expect((screen.getByRole('textbox', { name: 'Note text' }) as HTMLTextAreaElement).value).toBe('Study recursion and trees and graphs');
  });

  it('goes back to the saved text with Escape after a text fails, without the error', async () => {
    vi.mocked(updateNote).mockRejectedValue({ code: 'NOTE_TOO_LONG', params: { max: 500 } });
    renderNotes();
    fireEvent.click(await screen.findByRole('button', { name: 'Open note: Study recursion' }));
    const box = screen.getByRole('textbox', { name: 'Note text' });
    fireEvent.change(box, { target: { value: 'a'.repeat(501) } });
    fireEvent.blur(box);
    await screen.findByRole('alert');

    fireEvent.keyDown(screen.getByRole('textbox', { name: 'Note text' }), { key: 'Escape' });

    expect(screen.queryByRole('textbox', { name: 'Note text' })).toBeNull();
    expect(screen.getByText('Study recursion')).toBeTruthy();
    expect(screen.queryByRole('alert')).toBeNull();
  });
});
