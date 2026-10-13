import { AnimatePresence, motion } from 'framer-motion';
import { CheckCircle2, Download, Lock, Target } from 'lucide-react';
import { useEffect, useMemo, useRef, useState, type CSSProperties, type PointerEvent } from 'react';
import { createPortal } from 'react-dom';
import { Button } from '../components/button/Button';
import { Mark } from '../components/brand/Logo';
import { Chip, ChipGroup } from '../components/chip/Chip';
import { Dialog } from '../components/overlay/Dialog';
import { useModal } from '../components/overlay/useModal';
import { ProgressBar } from '../components/progress/Progress';
import { Segmented } from '../components/segmented/Segmented';
import { cx } from '../utils/cx';
import { prefersReducedMotion } from '../utils/shared';
import { AchievementArt } from './AchievementArt';
import {
  ACHIEVEMENT_CATEGORIES,
  ACHIEVEMENTS,
  ACHIEVEMENTS_BY_ID,
  RARITY,
  type AchievementCategory,
  type AchievementDef,
  type AchievementId,
  type AchievementState,
} from './catalog';
import './achievements.css';

export type AchievementStatus = 'earned' | 'progress' | 'locked';

/** Earned if it has a date; in progress if some progress exists; otherwise locked. */
export function achievementStatus(def: AchievementDef, state?: Partial<AchievementState>): AchievementStatus {
  if (state?.earnedAt) return 'earned';
  if (state?.progress && state.progress > 0 && def.goal > 1) return 'progress';
  return 'locked';
}

const fmtDate = (d: string | Date, locale: string) => new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric' }).format(typeof d === 'string' ? new Date(d) : d);

const tileVars = (def: AchievementDef) => ({ ['--t1' as string]: def.tint[0], ['--t2' as string]: def.tint[1] }) as CSSProperties;

// ─── Rarity pill ───────────────────────────────────────────
export function RarityPill({ rarity, inline }: { rarity: AchievementDef['rarity']; inline?: boolean }) {
  const r = RARITY[rarity];
  return (
    <span className={cx('sm-ach-rarity', inline && 'sm-ach-rarity--inline')} style={{ ['--rarity' as string]: r.color } as CSSProperties}>
      <i aria-hidden />
      {r.label}
    </span>
  );
}

// ─── Tile with tilt + glare ────────────────────────────────
export interface AchievementTileProps {
  id: AchievementId;
  locked?: boolean;
  /** Pixel size of the illustration inside the tile. */
  artSize?: number;
  showRarity?: boolean;
  /** 3D tilt and glare under the pointer. */
  tilt?: boolean;
  animated?: boolean;
  className?: string;
  style?: CSSProperties;
}

/** The coloured stage with the 3D object. Tilts toward the pointer like a real card. */
export function AchievementTile({ id, locked, artSize = 132, showRarity = true, tilt = true, animated = true, className, style }: AchievementTileProps) {
  const def = ACHIEVEMENTS_BY_ID[id];
  const ref = useRef<HTMLDivElement>(null);
  const onMove = (e: PointerEvent<HTMLDivElement>) => {
    if (!tilt || e.pointerType !== 'mouse' || !ref.current) return;
    const r = ref.current.getBoundingClientRect();
    const px = (e.clientX - r.left) / r.width;
    const py = (e.clientY - r.top) / r.height;
    const el = ref.current;
    el.dataset.tilting = 'true';
    el.style.setProperty('--ry', `${(px - 0.5) * 16}deg`);
    el.style.setProperty('--rx', `${(0.5 - py) * 14}deg`);
    el.style.setProperty('--gx', `${px * 100}%`);
    el.style.setProperty('--gy', `${py * 100}%`);
  };
  const onLeave = () => {
    const el = ref.current;
    if (!el) return;
    el.dataset.tilting = 'false';
    el.style.setProperty('--rx', '0deg');
    el.style.setProperty('--ry', '0deg');
  };
  if (!def) return null;
  return (
    <div
      ref={ref}
      className={cx('sm-ach-tile', def.dark && !locked && 'sm-ach-tile--dark', locked && 'sm-ach-tile--locked', !locked && `sm-ach-tile--${def.rarity}`, className)}
      style={{ ...tileVars(def), ...style }}
      onPointerMove={onMove}
      onPointerLeave={onLeave}
    >
      {showRarity && <RarityPill rarity={def.rarity} />}
      <AchievementArt id={id} size={artSize} locked={locked} animated={animated && !locked} title="" />
      {locked && (
        <span className="sm-ach-lock" aria-hidden>
          <Lock size={14} strokeWidth={2.5} />
        </span>
      )}
      <span className="sm-ach-tile__glare" aria-hidden />
    </div>
  );
}

// ─── Card ──────────────────────────────────────────────────
export interface AchievementCardProps {
  id: AchievementId;
  earnedAt?: string | Date;
  progress?: number;
  onClick?: () => void;
  layout?: 'vertical' | 'horizontal';
  locale?: string;
  className?: string;
}

/** Figure + name + short description, with the earned date, progress, or how to unlock it. */
export function AchievementCard({ id, earnedAt, progress, onClick, layout = 'vertical', locale = 'en-GB', className }: AchievementCardProps) {
  const def = ACHIEVEMENTS_BY_ID[id];
  if (!def) return null;
  const status = achievementStatus(def, { earnedAt, progress });
  const locked = status !== 'earned';
  const horizontal = layout === 'horizontal';
  const label = `${def.name}. ${RARITY[def.rarity].label}. ${status === 'earned' ? `Earned ${fmtDate(earnedAt!, locale)}` : status === 'progress' ? `${progress} of ${def.goal} ${def.unit ?? ''}` : 'Locked'}.`;
  const inner = (
    <>
      <AchievementTile id={id} locked={locked} artSize={horizontal ? 72 : 132} showRarity={!horizontal} tilt={!horizontal} />
      <div className="sm-ach-card__body">
        <div className="sm-ach-card__name">{def.name}</div>
        <div className="sm-ach-card__desc">{status === 'locked' ? def.criteria : def.description}</div>
        <div className="sm-ach-card__foot">
          {status === 'earned' && (
            <span className="sm-ach-card__earned">
              <CheckCircle2 size={15} strokeWidth={2.5} aria-hidden />
              Earned {fmtDate(earnedAt!, locale)}
            </span>
          )}
          {status === 'progress' && (
            <>
              <span className="sm-ach-card__progress-text">
                <span>In progress</span>
                <span>
                  {progress} / {def.goal} {def.unit}
                </span>
              </span>
              <ProgressBar value={progress} max={def.goal} label={`${def.name} progress`} size="sm" />
            </>
          )}
          {status === 'locked' && (
            <span className="sm-ach-card__criteria">
              <Lock size={13} strokeWidth={2.5} aria-hidden />
              Locked
            </span>
          )}
        </div>
      </div>
    </>
  );
  const cls = cx('sm-ach-card', horizontal && 'sm-ach-card--horizontal', className);
  return onClick ? (
    <button type="button" className={cls} data-state={status} onClick={onClick} aria-label={label}>
      {inner}
    </button>
  ) : (
    <article className={cls} data-state={status} aria-label={label}>
      {inner}
    </article>
  );
}

// ─── Badge (medallion) ─────────────────────────────────────
export interface AchievementBadgeProps {
  id: AchievementId;
  size?: number;
  locked?: boolean;
  onClick?: () => void;
  className?: string;
}

/** Round medallion for profiles and compact lists. The ring shows the rarity. */
export function AchievementBadge({ id, size = 64, locked, onClick, className }: AchievementBadgeProps) {
  const def = ACHIEVEMENTS_BY_ID[id];
  if (!def) return null;
  const style = { ...tileVars(def), width: size, height: size, ['--ring' as string]: RARITY[def.rarity].color } as CSSProperties;
  const content = (
    <>
      <span className="sm-ach-badge__disc">
        <AchievementArt id={id} size={size * 0.86} locked={locked} animated={false} title="" />
      </span>
      {locked && (
        <span className="sm-ach-badge__lock" aria-hidden>
          <Lock size={Math.max(9, size * 0.16)} strokeWidth={2.75} />
        </span>
      )}
    </>
  );
  const label = `${def.name}${locked ? ' (locked)' : ''}`;
  return onClick ? (
    <button type="button" className={cx('sm-ach-badge', locked && 'sm-ach-badge--locked', className)} style={style} onClick={onClick} aria-label={label} title={def.name}>
      {content}
    </button>
  ) : (
    <span className={cx('sm-ach-badge', locked && 'sm-ach-badge--locked', className)} style={style} role="img" aria-label={label} title={def.name}>
      {content}
    </span>
  );
}

// ─── Detail dialog ─────────────────────────────────────────
export interface AchievementDialogProps {
  id: AchievementId | null;
  earnedAt?: string | Date;
  progress?: number;
  onClose: () => void;
  /** Shows "Export certificate" for earned achievements (Export Achievement Certificate use case). */
  onExportCertificate?: (id: AchievementId) => void;
  locale?: string;
}

export function AchievementDialog({ id, earnedAt, progress, onClose, onExportCertificate, locale = 'en-GB' }: AchievementDialogProps) {
  const def = id ? ACHIEVEMENTS_BY_ID[id] : null;
  const status = def ? achievementStatus(def, { earnedAt, progress }) : 'locked';
  return (
    <Dialog
      open={!!def}
      onClose={onClose}
      title={<span className="sm-visually-hidden">{def?.name}</span>}
      size="sm"
      footer={
        def && (
          <>
            <Button variant="secondary" onClick={onClose}>
              Close
            </Button>
            {status === 'earned' && onExportCertificate && (
              <Button icon={Download} onClick={() => onExportCertificate(def.id)}>
                Export certificate
              </Button>
            )}
          </>
        )
      }
    >
      {def && (
        <div className="sm-ach-detail">
          <AchievementTile id={def.id} locked={status !== 'earned'} artSize={190} showRarity={false} />
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', justifyContent: 'center' }}>
            <RarityPill rarity={def.rarity} inline />
            <span className="sm-ach-rarity sm-ach-rarity--inline">{ACHIEVEMENT_CATEGORIES[def.category]}</span>
          </div>
          <h3 className="sm-ach-detail__name">{def.name}</h3>
          <p className="sm-ach-detail__desc">{def.description}</p>
          <div className="sm-ach-detail__how">
            <b>How to earn it</b>
            {def.criteria}
          </div>
          <div className="sm-ach-detail__status">
            {status === 'earned' && (
              <span className="sm-ach-card__earned">
                <CheckCircle2 size={16} strokeWidth={2.5} aria-hidden /> Earned on {fmtDate(earnedAt!, locale)}
              </span>
            )}
            {status === 'progress' && (
              <ProgressBar value={progress} max={def.goal} label="Progress" showLabel valueText={`${progress} / ${def.goal} ${def.unit ?? ''}`} />
            )}
            {status === 'locked' && (
              <span className="sm-ach-card__criteria" style={{ color: 'var(--sm-text-2)', fontSize: 14 }}>
                <Target size={15} strokeWidth={2.25} aria-hidden /> Not earned yet. Achievements are awarded automatically.
              </span>
            )}
          </div>
        </div>
      )}
    </Dialog>
  );
}

// ─── Grid ──────────────────────────────────────────────────
export interface AchievementGridProps {
  /** What the student has: earned dates and progress. Missing ids are locked. */
  states: AchievementState[];
  /** Restrict or reorder the catalogue. Defaults to all achievements. */
  achievements?: AchievementDef[];
  /** Show the counter and filters. */
  showSummary?: boolean;
  showFilters?: boolean;
  /** Override the built-in detail dialog. */
  onSelect?: (id: AchievementId) => void;
  onExportCertificate?: (id: AchievementId) => void;
  layout?: 'vertical' | 'horizontal';
  locale?: string;
  className?: string;
}

type Filter = 'all' | 'earned' | 'locked';

/** The View Achievements page in one component: summary, filters, cards and details. */
export function AchievementGrid({ states, achievements = ACHIEVEMENTS, showSummary = true, showFilters = true, onSelect, onExportCertificate, layout = 'vertical', locale = 'en-GB', className }: AchievementGridProps) {
  const [filter, setFilter] = useState<Filter>('all');
  const [category, setCategory] = useState<AchievementCategory | null>(null);
  const [open, setOpen] = useState<AchievementId | null>(null);
  const byId = useMemo(() => new Map(states.map((s) => [s.id, s])), [states]);

  const rows = useMemo(() => {
    const status = (d: AchievementDef) => achievementStatus(d, byId.get(d.id));
    const rank = (d: AchievementDef) => {
      const s = status(d);
      if (s === 'earned') return 0;
      if (s === 'progress') return 1;
      return 2;
    };
    return achievements
      .filter((d) => (filter === 'all' ? true : filter === 'earned' ? status(d) === 'earned' : status(d) !== 'earned'))
      .filter((d) => !category || d.category === category)
      .sort((a, b) => {
        const r = rank(a) - rank(b);
        if (r) return r;
        if (rank(a) === 0) return +new Date(byId.get(b.id)!.earnedAt!) - +new Date(byId.get(a.id)!.earnedAt!);
        if (rank(a) === 1) return (byId.get(b.id)!.progress ?? 0) / b.goal - (byId.get(a.id)!.progress ?? 0) / a.goal;
        return RARITY[a.rarity].order - RARITY[b.rarity].order;
      });
  }, [achievements, byId, filter, category]);

  const earned = achievements.filter((d) => achievementStatus(d, byId.get(d.id)) === 'earned');
  const select = (id: AchievementId) => (onSelect ? onSelect(id) : setOpen(id));
  const openState = open ? byId.get(open) : undefined;

  return (
    <div className={cx('sm-ach-grid-wrap', className)}>
      {showSummary && (
        <div className="sm-ach-summary">
          <div className="sm-ach-summary__count">
            {earned.length}
            <small>/ {achievements.length} earned</small>
          </div>
          <div className="sm-ach-summary__bar">
            <ProgressBar value={earned.length} max={achievements.length} label="Achievements earned" />
            <span className="sm-caption">Achievements are awarded automatically when you meet their criteria.</span>
          </div>
          <div className="sm-ach-summary__badges" aria-hidden>
            {earned.slice(0, 5).map((d) => (
              <AchievementBadge key={d.id} id={d.id} size={44} />
            ))}
          </div>
        </div>
      )}
      {showFilters && (
        <div className="sm-ach-toolbar">
          <Segmented<Filter>
            label="Filter achievements"
            size="sm"
            value={filter}
            onValueChange={setFilter}
            options={[
              { value: 'all', label: 'All' },
              { value: 'earned', label: `Earned · ${earned.length}` },
              { value: 'locked', label: 'To unlock' },
            ]}
          />
          <ChipGroup>
            {(Object.keys(ACHIEVEMENT_CATEGORIES) as AchievementCategory[]).map((c) => (
              <Chip key={c} size="sm" selected={category === c} onClick={() => setCategory(category === c ? null : c)}>
                {ACHIEVEMENT_CATEGORIES[c]}
              </Chip>
            ))}
          </ChipGroup>
        </div>
      )}
      <motion.div layout className="sm-ach-grid" style={layout === 'horizontal' ? { gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))' } : undefined}>
        <AnimatePresence initial={false}>
          {rows.map((d) => {
            const s = byId.get(d.id);
            return (
              <motion.div key={d.id} layout initial={{ opacity: 0, scale: 0.94 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.94 }} transition={{ duration: 0.3 }}>
                <AchievementCard id={d.id} earnedAt={s?.earnedAt} progress={s?.progress} onClick={() => select(d.id)} layout={layout} locale={locale} />
              </motion.div>
            );
          })}
        </AnimatePresence>
      </motion.div>
      {rows.length === 0 && <p className="sm-caption" style={{ textAlign: 'center', padding: 24 }}>Nothing here yet.</p>}
      {!onSelect && <AchievementDialog id={open} earnedAt={openState?.earnedAt} progress={openState?.progress} onClose={() => setOpen(null)} onExportCertificate={onExportCertificate} locale={locale} />}
    </div>
  );
}

// ─── Unlocked celebration ──────────────────────────────────
const CONFETTI = ['#d9ff3f', '#7a6bff', '#2a9fd6', '#eb6834', '#1baf7a', '#ff4f8e', '#ffc93c'];

export interface AchievementUnlockedProps {
  id: AchievementId | null;
  onClose: () => void;
  /** Secondary action, e.g. navigate to the achievements page. */
  onView?: () => void;
  viewLabel?: string;
}

/** Full-screen moment when the System awards an achievement. */
export function AchievementUnlocked({ id, onClose, onView, viewLabel = 'View achievements' }: AchievementUnlockedProps) {
  const def = id ? ACHIEVEMENTS_BY_ID[id] : null;
  const panel = useRef<HTMLDivElement>(null);
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  useModal(!!def, onClose, panel);
  const pieces = useMemo(
    () =>
      Array.from({ length: 36 }, (_, i) => ({
        x: (Math.random() - 0.5) * 760,
        y: (Math.random() - 0.75) * 620,
        r: Math.random() * 720 - 360,
        c: CONFETTI[i % CONFETTI.length],
        d: Math.random() * 0.25,
        s: 0.6 + Math.random() * 0.8,
      })),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [id],
  );
  if (!mounted) return null;
  const calm = prefersReducedMotion();
  return createPortal(
    <AnimatePresence>
      {def && (
        <>
          <motion.div key="scrim" className="sm-ach-unlock-scrim" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} />
          <motion.div
            key="panel"
            ref={panel}
            className="sm-ach-unlock"
            role="dialog"
            aria-modal="true"
            aria-labelledby="sm-ach-unlock-title"
            tabIndex={-1}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={(e) => e.target === e.currentTarget && onClose()}
          >
            <div className="sm-ach-unlock__inner">
              {!calm &&
                pieces.map((p, i) => (
                  <motion.span
                    key={i}
                    className="sm-ach-confetti"
                    style={{ background: p.c, scale: p.s }}
                    initial={{ x: 0, y: 0, rotate: 0, opacity: 1 }}
                    animate={{ x: p.x, y: [0, p.y, p.y + 420], rotate: p.r, opacity: [1, 1, 0] }}
                    transition={{ duration: 2.4, delay: 0.25 + p.d, ease: [0.2, 0.8, 0.4, 1] }}
                    aria-hidden
                  />
                ))}
              <div className="sm-ach-unlock__stage">
                <span className="sm-ach-unlock__rays" aria-hidden />
                <motion.div
                  initial={calm ? { opacity: 0 } : { scale: 0.2, rotate: -25, opacity: 0 }}
                  animate={{ scale: 1, rotate: 0, opacity: 1 }}
                  transition={{ type: 'spring', stiffness: 220, damping: 14, delay: 0.1 }}
                >
                  <AchievementTile id={def.id} artSize={170} showRarity={false} />
                </motion.div>
              </div>
              <motion.span className="sm-ach-unlock__eyebrow" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.45 }}>
                Achievement unlocked · {RARITY[def.rarity].label}
              </motion.span>
              <motion.h2 id="sm-ach-unlock-title" className="sm-ach-unlock__name" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.55 }}>
                {def.name}
              </motion.h2>
              <motion.p className="sm-ach-unlock__desc" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.65 }}>
                {def.description}
              </motion.p>
              <motion.div className="sm-ach-unlock__actions" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.75 }}>
                {onView && (
                  <Button variant="secondary" onClick={onView} style={{ background: 'rgba(255,255,255,.14)', color: '#fff' }}>
                    {viewLabel}
                  </Button>
                )}
                <Button variant="white" onClick={onClose} data-autofocus>
                  Nice!
                </Button>
              </motion.div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>,
    document.body,
  );
}

// ─── Certificate ───────────────────────────────────────────
export interface AchievementCertificateProps {
  student: { name: string; number: string; programme: string };
  items: { id: AchievementId; earnedAt: string | Date }[];
  /** Verification code printed on the PDF. */
  verificationCode: string;
  issuedAt?: Date;
  verifyUrl?: string;
  locale?: string;
  className?: string;
}

/**
 * Printable certificate (Export Achievement Certificate use case).
 * Render it in a print view and call `window.print()`, or send its data to your PDF service.
 */
export function AchievementCertificate({ student, items, verificationCode, issuedAt = new Date(), verifyUrl = 'studymatch.app/verify', locale = 'en-GB', className }: AchievementCertificateProps) {
  return (
    <section className={cx('sm-cert', className)} aria-label="Achievement certificate">
      <div className="sm-cert__head">
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}>
          <Mark size={34} />
          <span style={{ fontSize: 17, fontWeight: 500, letterSpacing: '-0.02em' }}>StudyMatch</span>
        </span>
        <span className="sm-eyebrow">Certificate of achievement</span>
      </div>
      <h2 className="sm-cert__title">{student.name}</h2>
      <p className="sm-cert__lead">
        Student <b>{student.number}</b> of <b>{student.programme}</b> has earned the following {items.length === 1 ? 'achievement' : `${items.length} achievements`} on the StudyMatch platform.
      </p>
      <ul className="sm-cert__list">
        {items.map((it) => {
          const def = ACHIEVEMENTS_BY_ID[it.id];
          if (!def) return null;
          return (
            <li key={it.id}>
              <AchievementBadge id={it.id} size={46} />
              <span style={{ minWidth: 0 }}>
                <b>{def.name}</b>
                <small>
                  {RARITY[def.rarity].label} · {fmtDate(it.earnedAt, locale)}
                </small>
              </span>
            </li>
          );
        })}
      </ul>
      <div className="sm-cert__foot">
        <span>
          Issued {fmtDate(issuedAt, locale)}
          <br />
          Verify at {verifyUrl}
        </span>
        <span style={{ textAlign: 'right' }}>
          Verification code
          <br />
          <span className="sm-cert__code">{verificationCode}</span>
        </span>
      </div>
    </section>
  );
}
