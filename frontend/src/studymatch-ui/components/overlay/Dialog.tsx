import { AnimatePresence,motion } from 'framer-motion';
import { AlertTriangle,X } from 'lucide-react';
import { useId,useRef,useState,type ReactNode,type RefObject } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { isBackendError,type FieldError } from '../../../api/errors';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import { EASE_OUT,type IconComponent } from '../../utils/shared';
import { useMounted } from '../../utils/useMounted';
import { Button } from '../button/Button';
import { Textarea } from '../field/Textarea';
import './overlay.css';
import { useModal } from './useModal';

export interface DialogProps {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  description?: ReactNode;
  children?: ReactNode;
  /** Buttons at the bottom. Put the primary action last. */
  footer?: ReactNode;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  /** Icon tile above the title. */
  icon?: IconComponent;
  iconTone?: 'violet' | 'danger' | 'success' | 'warning';
  /** Escape, scrim click and the × close the dialog. Turn off for blocking flows. */
  dismissible?: boolean;
  /** Element focused on open. Defaults to the first focusable element or `[data-autofocus]`. */
  initialFocus?: RefObject<HTMLElement>;
  className?: string;
}

const ICON_TONE = {
  violet: ['var(--sm-violet-50)', 'var(--sm-violet)'],
  danger: ['var(--sm-danger-50)', 'var(--sm-danger)'],
  success: ['var(--sm-success-50)', 'var(--sm-success)'],
  warning: ['var(--sm-warning-50)', 'var(--sm-warning-ink)'],
} as const;

/** Centred modal with focus trap, scroll lock and labelled title/description. */
export function Dialog({ open, onClose, title, description, children, footer, size = 'md', icon: Icon, iconTone = 'violet', dismissible = true, initialFocus, className }: DialogProps) {
  useTranslation();
  const panel = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const descId = useId();
  const mounted = useMounted();
  useModal(open, onClose, panel, { dismissible, initialFocus });
  if (!mounted) return null;
  return createPortal(
    <AnimatePresence>
      {open && (
        <>
          <motion.div key="scrim" className="sm-scrim" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.2 }} onClick={dismissible ? onClose : undefined} />
          <div key="wrap" className="sm-dialog-wrap">
            <motion.div
              ref={panel}
              className={cx('sm-dialog', size !== 'md' && `sm-dialog--${size}`, className)}
              role="dialog"
              aria-modal="true"
              aria-labelledby={titleId}
              aria-describedby={description ? descId : undefined}
              tabIndex={-1}
              initial={{ opacity: 0, y: 24, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 12, scale: 0.98 }}
              transition={{ duration: 0.38, ease: EASE_OUT }}
            >
              {dismissible && (
                <button type="button" data-close className="sm-icon-btn sm-icon-btn--sm sm-icon-btn--secondary sm-dialog__close" onClick={onClose} aria-label={i18n.t('ui.close')}>
                  <X size={16} strokeWidth={2.5} />
                </button>
              )}
              {Icon && (
                <div className="sm-dialog__icon" style={{ background: ICON_TONE[iconTone][0], color: ICON_TONE[iconTone][1] }}>
                  <Icon size={26} strokeWidth={2} aria-hidden />
                </div>
              )}
              <h2 id={titleId} className="sm-dialog__title">
                {title}
              </h2>
              {description && (
                <p id={descId} className="sm-dialog__desc">
                  {description}
                </p>
              )}
              {children && <div className="sm-dialog__body">{children}</div>}
              {footer && <div className="sm-dialog__footer">{footer}</div>}
            </motion.div>
          </div>
        </>
      )}
    </AnimatePresence>,
    document.body,
  );
}

export interface ConfirmDialogProps {
  fieldErrors?: FieldError[];
  open: boolean;
  onClose: () => void;
  /** Can be async: the button shows a spinner until it resolves. Receives the reason when `reason` is set. */
  onConfirm: (reason: string) => void | Promise<void>;
  title: ReactNode;
  description?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: 'danger' | 'primary';
  /** Ask for a written reason (e.g. Block Account). */
  reason?: { label: string; placeholder?: string; required?: boolean; maxLength?: number };
  icon?: IconComponent;
}

/** Confirmation for consequential actions: publish groups, block an account, cancel a StudyRoom. */
function ConfirmDialogContent({ open, onClose, onConfirm, title, description, confirmLabel = i18n.t('ui.confirm'), cancelLabel = i18n.t('ui.cancel'), tone = 'primary', reason, icon, fieldErrors = [] }: ConfirmDialogProps) {
  useTranslation();
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [caughtFields, setCaughtFields] = useState<FieldError[]>([]);
  const reasonError = [...fieldErrors, ...caughtFields].filter(field => field.field === 'reason')
    .map(field => i18n.t(`validation.${field.code}`, field.params ?? {})).join(' ');

  const missing = !!reason?.required && !text.trim();
  const confirm = async () => {
    if (missing) {
      setError(i18n.t('common.required', {field: reason?.label}));
      return;
    }
    try {
      setBusy(true);
      setCaughtFields([]);
      await onConfirm(text.trim());
      onClose();
    } catch (e) {
      if (isBackendError(e)) setCaughtFields(e.fieldErrors ?? []);
      setError(isBackendError(e) ? i18n.t(`errors.${e.code}`, e.params ?? {}) : i18n.t('common.unexpectedError'));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog
      open={open}
      onClose={busy ? () => {} : onClose}
      title={title}
      description={description}
      size="sm"
      icon={icon ?? (tone === 'danger' ? AlertTriangle : undefined)}
      iconTone={tone === 'danger' ? 'danger' : 'violet'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            {cancelLabel}
          </Button>
          <Button variant={tone === 'danger' ? 'danger-solid' : 'primary'} onClick={confirm} loading={busy}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      {reason && (
        <Textarea
          label={reason.label}
          placeholder={reason.placeholder}
          value={text}
          maxLength={reason.maxLength}
          onChange={(e) => {
            setText(e.target.value);
            setError(null);
          }}
          error={reasonError || error || undefined}
          data-autofocus
        />
      )}
      {!reason && error && <p style={{ color: 'var(--sm-danger-ink)', margin: 0, fontSize: 14 }}>{error}</p>}
    </Dialog>
  );
}

export interface SheetProps {
  open: boolean;
  onClose: () => void;
  title?: ReactNode;
  /** Accessible name when there is no visible title. */
  label?: string;
  children: ReactNode;
  /** Bottom sheet (mobile-first) or side panel (desktop detail views). */
  side?: 'bottom' | 'right';
  className?: string;
}

/** Bottom sheet or right-side panel for details that keep the page context. */
export function Sheet({ open, onClose, title, label, children, side = 'bottom', className }: SheetProps) {
  useTranslation();
  const panel = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const mounted = useMounted();
  useModal(open, onClose, panel);
  if (!mounted) return null;
  const from = side === 'bottom' ? { y: '100%' } : { x: '100%' };
  return createPortal(
    <AnimatePresence>
      {open && (
        <>
          <motion.div key="scrim" className="sm-scrim" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} />
          <motion.div
            key="sheet"
            ref={panel}
            className={cx('sm-sheet', `sm-sheet--${side}`, className)}
            role="dialog"
            aria-modal="true"
            aria-label={title ? undefined : label}
            aria-labelledby={title ? titleId : undefined}
            tabIndex={-1}
            initial={from}
            animate={{ x: 0, y: 0 }}
            exit={from}
            transition={{ duration: 0.45, ease: EASE_OUT }}
          >
            {side === 'bottom' && <div className="sm-sheet__grab" aria-hidden />}
            {title && (
              <div className="sm-sheet__head">
                <h2 id={titleId} className="sm-h3">
                  {title}
                </h2>
                <button type="button" data-close className="sm-icon-btn sm-icon-btn--sm sm-icon-btn--secondary" onClick={onClose} aria-label={i18n.t('ui.close')}>
                  <X size={16} strokeWidth={2.5} />
                </button>
              </div>
            )}
            {children}
          </motion.div>
        </>
      )}
    </AnimatePresence>,
    document.body,
  );
}

export function ConfirmDialog(props: ConfirmDialogProps) { return props.open ? <ConfirmDialogContent {...props} /> : null; }
