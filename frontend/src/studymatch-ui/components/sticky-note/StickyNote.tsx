import { AnimatePresence,motion } from 'framer-motion';
import { Plus,X } from 'lucide-react';
import { useRef,useState,type KeyboardEvent,type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import '../button/button.css';
import './sticky-note.css';
import { NOTE_COLORS,NOTE_HEX,tiltFor } from './StickyNote.shared';

export type NoteColor = 'yellow' | 'pink' | 'blue' | 'green' | 'orange' | 'violet';

function Check() {
  useTranslation();
  return (
    <svg className="sm-note__check" viewBox="0 0 60 50" aria-hidden>
      <motion.path
        d="M6 28 C 12 32, 18 38, 22 44 C 30 28, 42 14, 56 4"
        fill="none"
        stroke="currentColor"
        strokeWidth="5"
        strokeLinecap="round"
        strokeLinejoin="round"
        initial={{ pathLength: 0 }}
        animate={{ pathLength: 1 }}
        transition={{ duration: 0.45, ease: [0.16, 1, 0.3, 1] }}
      />
    </svg>
  );
}

export interface StickyNoteProps {
  color?: NoteColor;
  title?: ReactNode;
  children?: ReactNode;
  footer?: ReactNode;
  done?: boolean;
  size?: number;
  rotate?: number;
  onClick?: () => void;
  onRemove?: () => void;
  label?: string;
}

/**
 * A paper sticky note: adhesive band, grain, a lifted corner and handwritten ink.
 * Clicking toggles it (e.g. done); hovering lifts the corner a little more.
 */
export function StickyNote({ color = 'yellow', title, children, footer, done, size = 220, rotate = 0, onClick, onRemove, label }: StickyNoteProps) {
  useTranslation();
  const onKey = (e: KeyboardEvent) => {
    if (onClick && (e.key === 'Enter' || e.key === ' ')) {
      e.preventDefault();
      onClick();
    }
  };
  return (
    <motion.div
      className="sm-note-wrap"
      style={{ ['--note-size' as string]: `${size}px`, rotate }}
      whileHover={{ y: -6, rotate: rotate * 0.4, scale: 1.02 }}
      whileTap={{ scale: 0.97 }}
      transition={{ type: 'spring', stiffness: 300, damping: 20 }}
      onClick={onClick}
      onKeyDown={onKey}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      aria-pressed={onClick ? !!done : undefined}
      aria-label={label}
    >
      <NoteFace color={color} title={title} footer={footer} done={done}>{children}</NoteFace>
      {onRemove && (
        <button
          type="button"
          className="sm-note__remove"
          aria-label={i18n.t('ui.remove_note')}
          onClick={(e) => {
            e.stopPropagation();
            onRemove();
          }}
        >
          <X size={14} strokeWidth={3} />
        </button>
      )}
    </motion.div>
  );
}

function NoteFace({ color, title, children, footer, done }: { color: NoteColor; title?: ReactNode; children?: ReactNode; footer?: ReactNode; done?: boolean }) {
  useTranslation();
  return (
    <div className={cx('sm-note', `sm-note--${color}`, done && 'sm-note--done')}>
      <span className="sm-note__grain" aria-hidden />
      {title && <div className="sm-note__title">{title}</div>}
      <div className="sm-note__body" style={done ? { textDecoration: 'line-through', textDecorationThickness: '2px' } : undefined}>
        {children}
      </div>
      {footer && <div className="sm-note__foot">{footer}</div>}
      {done && <Check />}
    </div>
  );
}

// ─── Board ─────────────────────────────────────────────────
export interface NoteData {
  id: string;
  text: string;
  title?: string;
  color: NoteColor;
  done?: boolean;
  meta?: string;
}

/**
 * A dotted board of sticky notes. Drag to rearrange, click to mark done,
 * hover and press × to peel one off. New notes are slapped on top.
 */
export function StickyBoard({
  initial,
  size = 200,
  composer = true,
  onChange,
  placeholder = i18n.t('ui.write_note'),
}: {
  /** Notes on first render. The board manages them afterwards and reports through `onChange`. */
  initial: NoteData[];
  size?: number;
  /** Show the "write a note" bar. */
  composer?: boolean;
  onChange?: (notes: NoteData[]) => void;
  placeholder?: string;
}) {
  useTranslation();
  const [notes, setNotes] = useState(initial);
  const [stack, setStack] = useState<string[]>(initial.map((n) => n.id));
  const [text, setText] = useState('');
  const [color, setColor] = useState<NoteColor>('yellow');
  const board = useRef<HTMLDivElement>(null);
  const dragged = useRef(false);

  const update = (next: NoteData[]) => {
    setNotes(next);
    onChange?.(next);
  };
  const front = (id: string) => setStack((s) => [...s.filter((x) => x !== id), id]);
  const add = () => {
    if (!text.trim()) return;
    const n: NoteData = { id: `n${Date.now()}`, text: text.trim(), color, meta: i18n.t('ui.just_now') };
    update([...notes, n]);
    setStack((s) => [...s, n.id]);
    setText('');
    setColor(NOTE_COLORS[(NOTE_COLORS.indexOf(color) + 1) % NOTE_COLORS.length]);
  };

  return (
    <div>
      {composer && (
        <form
          className="sm-note-composer"
          onSubmit={(e) => {
            e.preventDefault();
            add();
          }}
        >
          <input value={text} onChange={(e) => setText(e.target.value)} placeholder={placeholder} aria-label={i18n.t('ui.new_note')} maxLength={80} />
          <div className="sm-note-swatches" role="radiogroup" aria-label={i18n.t('ui.note_colour')}>
            {NOTE_COLORS.map((c) => (
              <button
                key={c}
                type="button"
                role="radio"
                aria-checked={c === color}
                aria-label={i18n.t(`colours.${c}`)}
                onClick={() => setColor(c)}
                style={{ background: NOTE_HEX[c], boxShadow: c === color ? '0 0 0 2px #fff, 0 0 0 4px var(--sm-black)' : 'inset 0 0 0 1px rgba(0,0,0,.08)' }}
              />
            ))}
          </div>
          <button type="submit" className="sm-btn sm-btn--primary sm-btn--sm" disabled={!text.trim()}>
            <Plus size={16} strokeWidth={2.5} aria-hidden />
            <span className="sm-btn__label"> {i18n.t('ui.stick')} </span>
          </button>
        </form>
      )}
      <div className="sm-board" ref={board}>
        <AnimatePresence>
          {notes.map((n) => {
            const r = tiltFor(n.id);
            return (
              <motion.div
                key={n.id}
                className="sm-note-wrap"
                style={{ ['--note-size' as string]: `${size}px`, zIndex: stack.indexOf(n.id) + 1 }}
                drag
                dragConstraints={board}
                dragElastic={0.12}
                dragMomentum={false}
                onPointerDown={() => {
                  dragged.current = false;
                  front(n.id);
                }}
                onDragStart={() => (dragged.current = true)}
                onClick={() => {
                  if (!dragged.current) update(notes.map((x) => (x.id === n.id ? { ...x, done: !x.done } : x)));
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    update(notes.map((x) => (x.id === n.id ? { ...x, done: !x.done } : x)));
                  }
                }}
                initial={{ opacity: 0, scale: 1.25, rotate: r + 10, y: -24 }}
                animate={{ opacity: 1, scale: 1, rotate: r, y: 0 }}
                exit={{ opacity: 0, rotate: r - 28, y: -70, x: 50, scale: 0.92, transition: { duration: 0.45, ease: [0.4, 0, 0.2, 1] } }}
                whileHover={{ scale: 1.03, rotate: r * 0.4 }}
                whileDrag={{ scale: 1.08, rotate: 0, cursor: 'grabbing' }}
                transition={{ type: 'spring', stiffness: 320, damping: 22 }}
                role="button"
                tabIndex={0}
                aria-pressed={!!n.done}
                aria-label={i18n.t('ui.note_label', {title:n.title ? n.title+': ' : '',text:n.text,done:n.done ? i18n.t('ui.note_done') : ''})}
              >
                <NoteFace color={n.color} title={n.title} done={n.done} footer={n.meta ? <><span>{n.meta}</span><span>{n.done ? i18n.t('ui.done') : ''}</span></> : undefined}>
                  {n.text}
                </NoteFace>
                <button
                  type="button"
                  className="sm-note__remove"
                  aria-label={i18n.t('ui.peel_off_note')}
                  onPointerDown={(e) => e.stopPropagation()}
                  onClick={(e) => {
                    e.stopPropagation();
                    update(notes.filter((x) => x.id !== n.id));
                  }}
                >
                  <X size={14} strokeWidth={3} />
                </button>
              </motion.div>
            );
          })}
        </AnimatePresence>
        {notes.length === 0 && (
          <div className="sm-board__empty"> {i18n.t('ui.the_board_is_clear')} </div>
        )}
      </div>
    </div>
  );
}
