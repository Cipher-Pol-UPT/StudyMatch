import { Plus } from 'lucide-react';
import { useEffect, useId, useRef, useState, type CSSProperties, type PointerEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { isBackendError } from '../../api/errors';
import { createNote, deleteNote, getNotes, updateNote } from '../../api/notes';
import { Banner, Button, Card, NOTE_HEX, Skeleton, StickyNote, tiltFor, type NoteColor } from '../../studymatch-ui';
import { NoteColour } from '../../types/enums';
import { MAX_NOTES, type NoteData } from '../../types/notes';
import './stickyNotes.css';

// the same width the side menu turns into the mobile menu at
const PHONE = '(max-width: 960px)';
// moving less than this is a click, not a drag
const DRAG_PX = 4;
// a note's side on the board; on a phone it sets the text size, the card takes the full width
const NOTE_PX = 168;
const PHONE_NOTE_PX = 160;
// where a new note can go, all inside the board even on a narrow one
const SPOTS = [4, 33, 62].flatMap(y => [3, 20, 37, 54, 71].map(x => ({ x, y })));

const byChange = (a: NoteData, b: NoteData) => a.updatedAt.localeCompare(b.updatedAt) || a.id - b.id;
const newestFirst = (a: NoteData, b: NoteData) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id;
const clamp = (value: number, max: number) => Math.min(Math.max(value, 0), max);
// the backend's colours are the library's, in capitals
const paper = (colour: NoteColour) => colour.toLowerCase() as NoteColor;

// the spot farthest from the notes already there
function freeSpot(notes: NoteData[]) {
  const distance = (spot: { x: number; y: number }) =>
    Math.min(Infinity, ...notes.map(note => Math.hypot(spot.x - note.x, spot.y - note.y)));
  return SPOTS.reduce((best, spot) => distance(spot) > distance(best) ? spot : best, SPOTS[0]);
}

function usePhone() {
  const [phone, setPhone] = useState(() => !!window.matchMedia?.(PHONE).matches);
  useEffect(() => {
    const query = window.matchMedia?.(PHONE);
    if (!query) return undefined;
    const change = () => setPhone(query.matches);
    query.addEventListener('change', change);
    return () => query.removeEventListener('change', change);
  }, []);
  return phone;
}

interface Drag {
  id: number;
  pointerX: number; pointerY: number;
  // where the note was, and how far it can go, in percent of the board
  x: number; y: number; maxX: number; maxY: number;
  width: number; height: number;
  moved: boolean;
  // where it is now, read on drop without waiting for a render
  to?: { x: number; y: number };
}

// private sticky notes on the home page: the library's board to drag them around, a notebook on a phone (#304)
export function StickyNotes() {
  const { t } = useTranslation();
  const titleId = useId();
  const phone = usePhone();
  const [notes, setNotes] = useState<NoteData[] | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  // the composer: what's typed and the colour picked for the next note
  const [draft, setDraft] = useState('');
  const [colour, setColour] = useState<NoteColour>('YELLOW');
  const [adding, setAdding] = useState(false);
  const [addError, setAddError] = useState<string>();
  // the note whose text is open, with what's typed so far
  const [editing, setEditing] = useState<{ id: number; text: string } | null>(null);
  // why the last change to a note failed, shown on it
  const [errors, setErrors] = useState<Record<number, string>>({});
  // a text that didn't save (too long), kept on its note until it does
  const [unsaved, setUnsaved] = useState<Record<number, string>>({});
  // a note being dragged, where it is now
  const [dragged, setDragged] = useState<{ id: number; x: number; y: number } | null>(null);
  const drag = useRef<Drag | null>(null);
  // the last drop of each note, so an older drop's answer never moves it back
  const drops = useRef(new Map<number, number>());
  const board = useRef<HTMLDivElement>(null);
  // the open note's text box, focused when a note opens so you can type straight away
  const editor = useRef<HTMLTextAreaElement>(null);
  const editingId = editing?.id;
  useEffect(() => {
    if (editingId !== undefined) editor.current?.focus();
  }, [editingId]);

  useEffect(() => {
    let alive = true;
    // an answer that isn't a list of notes counts as a failed load too
    getNotes()
      .then(loaded => { if (alive) setNotes([...loaded].sort(byChange)); })
      .catch(() => { if (alive) setLoadFailed(true); });
    return () => { alive = false; };
  }, []);

  const describe = (error: unknown) => isBackendError(error) ? t(`errors.${error.code}`, error.params ?? {}) : t('common.unexpectedError');
  // the backend's version of a note, moved to the end: the one changed last is drawn on top.
  // One deleted meanwhile stays gone
  const put = (note: NoteData) => setNotes(current => current && (current.some(other => other.id === note.id)
    ? [...current.filter(other => other.id !== note.id), note]
    : current));
  const forget = (id: number) => {
    setNotes(current => current && current.filter(note => note.id !== id));
    setEditing(current => current?.id === id ? null : current);
  };
  // sets or clears one note's entry in errors or unsaved
  const setFor = (set: typeof setErrors) => (id: number, text?: string) => set(current => {
    const next = { ...current };
    if (text === undefined) delete next[id];
    else next[id] = text;
    return next;
  });
  const showError = setFor(setErrors);
  const keepUnsaved = setFor(setUnsaved);
  // a note that's gone (deleted in another tab) leaves the board, any other failure shows under it
  const refused = (id: number, error: unknown) => {
    if (isBackendError(error) && error.code === 'NOT_FOUND') forget(id);
    else showError(id, describe(error));
  };

  const add = async () => {
    if (!notes) return;
    setAdding(true);
    setAddError(undefined);
    try {
      const created = await createNote({ text: draft, colour, ...freeSpot(notes) });
      setNotes(current => current && [...current, created]);
      setDraft('');
      // the next one in another colour, like the library's board
      setColour(NoteColour[(NoteColour.indexOf(colour) + 1) % NoteColour.length]);
    } catch (error) {
      setAddError(describe(error));
    } finally {
      setAdding(false);
    }
  };

  const openNote = (note: NoteData) => setEditing({ id: note.id, text: unsaved[note.id] ?? note.text });

  // saves when you click away. A text that doesn't save stays on its note with the error, even once another note is open
  const saveText = async (note: NoteData) => {
    if (editing?.id !== note.id) return;
    const { text } = editing;
    if (text.trim() === note.text) {
      setEditing(null);
      showError(note.id);
      keepUnsaved(note.id);
      return;
    }
    try {
      put(await updateNote(note.id, { text }));
      showError(note.id);
      keepUnsaved(note.id);
      setEditing(current => current?.id === note.id ? null : current);
    } catch (error) {
      keepUnsaved(note.id, text);
      refused(note.id, error);
    }
  };

  const recolour = async (note: NoteData, next: NoteColour) => {
    try {
      put(await updateNote(note.id, { colour: next }));
    } catch (error) {
      refused(note.id, error);
    }
  };

  const remove = async (note: NoteData) => {
    try {
      await deleteNote(note.id);
      forget(note.id);
    } catch (error) {
      refused(note.id, error);
    }
  };

  const startDrag = (event: PointerEvent<HTMLElement>, note: NoteData) => {
    const box = board.current?.getBoundingClientRect();
    const onControl = (event.target as HTMLElement).closest('button, textarea');
    if (event.button !== 0 || onControl || editing?.id === note.id || !box?.width || !box.height) return;
    const { offsetWidth, offsetHeight } = event.currentTarget;
    const maxX = Math.max(0, (box.width - offsetWidth) / box.width * 100);
    const maxY = Math.max(0, (box.height - offsetHeight) / box.height * 100);
    drag.current = {
      id: note.id, pointerX: event.clientX, pointerY: event.clientY, x: Math.min(note.x, maxX), y: Math.min(note.y, maxY),
      maxX, maxY, width: box.width, height: box.height, moved: false,
    };
  };

  const moveDrag = (event: PointerEvent<HTMLElement>) => {
    const current = drag.current;
    if (!current) return;
    // the button was let go somewhere else, so this is just the mouse passing over
    if (event.buttons === 0) return cancelDrag();
    const dx = event.clientX - current.pointerX;
    const dy = event.clientY - current.pointerY;
    if (!current.moved) {
      if (Math.hypot(dx, dy) < DRAG_PX) return;
      // only now, so a plain click still reaches the note and opens it
      current.moved = true;
      event.currentTarget.setPointerCapture?.(event.pointerId);
    }
    current.to = {
      x: clamp(current.x + dx / current.width * 100, current.maxX),
      y: clamp(current.y + dy / current.height * 100, current.maxY),
    };
    setDragged({ id: current.id, ...current.to });
  };

  // only the position changes, whatever else was saved meanwhile
  const place = (id: number, x: number, y: number) =>
    setNotes(current => current && [...current.filter(other => other.id !== id), ...current.filter(other => other.id === id).map(other => ({ ...other, x, y }))]);

  // saves once, on drop. If that fails the note goes back where it was; a later drop wins over an earlier one
  const endDrag = async (note: NoteData) => {
    const current = drag.current;
    drag.current = null;
    setDragged(null);
    if (!current?.moved || !current.to || current.id !== note.id) return;
    const { x, y } = current.to;
    const drop = (drops.current.get(note.id) ?? 0) + 1;
    drops.current.set(note.id, drop);
    const latest = () => drops.current.get(note.id) === drop;
    place(note.id, x, y);
    try {
      const saved = await updateNote(note.id, { x, y });
      if (latest()) put(saved);
      showError(note.id);
    } catch (error) {
      if (isBackendError(error) && error.code === 'NOT_FOUND') return forget(note.id);
      if (!latest()) return;
      place(note.id, note.x, note.y);
      showError(note.id, t('notes.moveFailed'));
    }
  };

  function cancelDrag() {
    drag.current = null;
    setDragged(null);
  }

  // keeps the text box focused, so picking a colour doesn't save the text first
  const keepFocus = (event: { preventDefault: () => void }) => event.preventDefault();

  const swatches = (picked: NoteColour, pick: (next: NoteColour) => void, focusSafe = false) => (
    <div className="sm-note-swatches" role="radiogroup" aria-label={t('ui.note_colour')}>
      {NoteColour.map(option => (
        <button key={option} type="button" role="radio" aria-checked={option === picked} aria-label={t(`colours.${paper(option)}`)}
          className={option === picked ? 'sticky-notes__swatch--on' : undefined} style={{ background: NOTE_HEX[paper(option)] }}
          onMouseDown={focusSafe ? keepFocus : undefined} onClick={() => pick(option)} />
      ))}
    </div>
  );

  const renderNote = (note: NoteData, onBoard: boolean) => {
    const isOpen = editing?.id === note.id;
    const isDragged = dragged?.id === note.id;
    const where = isDragged ? dragged : note;
    const size = onBoard ? NOTE_PX : PHONE_NOTE_PX;
    const style = { '--note-px': `${size}px` } as CSSProperties;
    if (onBoard) Object.assign(style, { left: `min(${where.x}%, calc(100% - var(--note-px)))`, top: `min(${where.y}%, calc(100% - var(--note-px)))` });
    const error = errors[note.id];
    // a text that didn't save shows until it does, so it isn't lost
    const text = unsaved[note.id] ?? note.text;
    return (
      <div key={note.id} className={`sticky-notes__spot${isDragged ? ' sticky-notes__spot--dragging' : ''}`} style={style}
        onPointerDown={onBoard ? event => startDrag(event, note) : undefined}
        onPointerMove={onBoard ? moveDrag : undefined}
        onPointerUp={onBoard ? () => void endDrag(note) : undefined}
        onPointerCancel={onBoard ? cancelDrag : undefined}>
        {isOpen ? (
          <StickyNote color={paper(note.colour)} size={size} onRemove={() => void remove(note)}
            footer={swatches(note.colour, next => void recolour(note, next), true)}>
            <textarea className="sticky-notes__input" aria-label={t('notes.text')} value={editing.text} ref={editor}
              aria-invalid={error ? true : undefined}
              onChange={event => setEditing({ id: note.id, text: event.target.value })}
              onBlur={() => void saveText(note)}
              onKeyDown={event => { if (event.key === 'Escape') setEditing(null); }} />
          </StickyNote>
        ) : (
          <StickyNote color={paper(note.colour)} size={size} rotate={isDragged || !onBoard ? 0 : tiltFor(String(note.id))}
            label={t('notes.open', { text: text || t('notes.emptyNote') })}
            onClick={() => openNote(note)} onRemove={() => void remove(note)}>
            {text || <span className="sticky-notes__placeholder">{t('notes.emptyNote')}</span>}
          </StickyNote>
        )}
        {error && <p className="sticky-notes__error" role="alert">{error}</p>}
      </div>
    );
  };

  const full = !!notes && notes.length >= MAX_NOTES;

  const content = () => {
    if (loadFailed) return <Banner tone="danger">{t('notes.loadFailed')}</Banner>;
    if (!notes) {
      return (
        <div className="sticky-notes__loading" aria-label={t('notes.loading')}>
          <Skeleton h={phone ? 180 : 460} r="var(--sm-r-lg)" />
        </div>
      );
    }
    if (phone) {
      return (
        <ul className="sticky-notes__notebook" aria-label={t('notes.title')}>
          {[...notes].sort(newestFirst).map(note => <li key={note.id}>{renderNote(note, false)}</li>)}
          {!notes.length && <li className="sticky-notes__empty">{t('notes.none')}</li>}
        </ul>
      );
    }
    return (
      <div className="sm-board sticky-notes__board" ref={board}>
        {notes.map(note => renderNote(note, true))}
        {!notes.length && <p className="sm-board__empty">{t('notes.none')}</p>}
      </div>
    );
  };

  return (
    <section aria-labelledby={titleId} className="sticky-notes">
      <Card>
        <header className="sticky-notes__head">
          <h2 id={titleId}>{t('notes.title')}</h2>
          <p className="sm-caption">{t('notes.intro')}</p>
        </header>

        <form className="sm-note-composer" onSubmit={event => { event.preventDefault(); void add(); }}>
          <input value={draft} onChange={event => setDraft(event.target.value)} placeholder={t('ui.write_note')} aria-label={t('ui.new_note')}
            disabled={!notes || full} />
          {swatches(colour, setColour)}
          <Button type="submit" icon={Plus} size="sm" loading={adding} disabled={!notes || full}>{t('ui.stick')}</Button>
        </form>
        {(addError || full) && (
          <p className="sticky-notes__add-error" role={addError ? 'alert' : undefined}>
            {addError ?? t('errors.NOTE_LIMIT_REACHED', { max: MAX_NOTES })}
          </p>
        )}

        {content()}
      </Card>
    </section>
  );
}
