import { AnimatePresence,motion } from 'framer-motion';
import { AlertTriangle,CheckCircle2,Info,XCircle } from 'lucide-react';
import { useCallback,useMemo,useRef,useState,type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { EASE_OUT,type IconComponent } from '../../utils/shared';
import { useMounted } from '../../utils/useMounted';
import './overlay.css';
import { ToastCtx } from './toastContext';

export type ToastTone = 'success' | 'info' | 'warning' | 'danger';

const TONE: Record<ToastTone, { icon: IconComponent; color: string }> = {
  success: { icon: CheckCircle2, color: 'var(--sm-spark)' },
  info: { icon: Info, color: '#a9a1ff' },
  warning: { icon: AlertTriangle, color: '#ffc94d' },
  danger: { icon: XCircle, color: '#ff7b76' },
};

export interface ToastProps {
  children: ReactNode;
  description?: ReactNode;
  tone?: ToastTone;
  icon?: IconComponent;
  action?: string;
  onAction?: () => void;
}

/** The black pill. Usually shown through `useToast()`, but can be rendered inline. */
export function Toast({ children, description, tone = 'success', icon, action, onAction }: ToastProps) {
  useTranslation();
  const Icon = icon ?? TONE[tone].icon;
  return (
    <div className="sm-toast">
      <Icon size={20} strokeWidth={2.25} color={TONE[tone].color} aria-hidden />
      <span className="sm-toast__text">
        {children}
        {description && <small>{description}</small>}
      </span>
      {action ? (
        <button type="button" className="sm-toast__action" onClick={onAction}>
          {action}
        </button>
      ) : (
        <span style={{ width: 8 }} />
      )}
    </div>
  );
}

export interface ToastOptions {
  message: ReactNode;
  description?: ReactNode;
  tone?: ToastTone;
  action?: string;
  onAction?: () => void;
  /** Milliseconds. Defaults to 4000 (6000 with an action). */
  duration?: number;
}

interface ToastItem extends ToastOptions {
  id: number;
}

export type ToastFn = ((input: ReactNode | ToastOptions) => number) & {
  success: (message: ReactNode, opts?: Omit<ToastOptions, 'message' | 'tone'>) => number;
  info: (message: ReactNode, opts?: Omit<ToastOptions, 'message' | 'tone'>) => number;
  warning: (message: ReactNode, opts?: Omit<ToastOptions, 'message' | 'tone'>) => number;
  danger: (message: ReactNode, opts?: Omit<ToastOptions, 'message' | 'tone'>) => number;
  dismiss: (id: number) => void;
};



function isOptions(x: unknown): x is ToastOptions {
  return typeof x === 'object' && x !== null && 'message' in x;
}

/** Hosts the toast stack. Already included in <StudyMatchProvider>. */
export function ToastProvider({ children, max = 3 }: { children: ReactNode; max?: number }) {
  useTranslation();
  const [items, setItems] = useState<ToastItem[]>([]);
  const mounted = useMounted();
  const seq = useRef(0);

  const dismiss = useCallback((id: number) => setItems((xs) => xs.filter((x) => x.id !== id)), []);

  const toast = useMemo(() => {
    const push = (input: ReactNode | ToastOptions) => {
      const opts: ToastOptions = isOptions(input) ? input : { message: input };
      const id = ++seq.current;
      setItems((xs) => [...xs.slice(-(max - 1)), { ...opts, id }]);
      const duration = opts.duration ?? (opts.action ? 6000 : 4000);
      if (duration !== Infinity) window.setTimeout(() => dismiss(id), duration);
      return id;
    };
    const fn = push as ToastFn;
    fn.success = (message, o) => push({ ...o, message, tone: 'success' });
    fn.info = (message, o) => push({ ...o, message, tone: 'info' });
    fn.warning = (message, o) => push({ ...o, message, tone: 'warning' });
    fn.danger = (message, o) => push({ ...o, message, tone: 'danger' });
    fn.dismiss = dismiss;
    return fn;
  }, [dismiss, max]);

  return (
    <ToastCtx.Provider value={toast}>
      {children}
      {mounted &&
        createPortal(
          <div className="sm-toast-region" role="region" aria-label={i18n.t('ui.notifications')} aria-live="polite">
            <AnimatePresence initial={false}>
              {items.map((t) => (
                <motion.div
                  key={t.id}
                  layout
                  initial={{ opacity: 0, y: 40, scale: 0.9 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: 20, scale: 0.95 }}
                  transition={{ duration: 0.42, ease: EASE_OUT }}
                  style={{ maxWidth: '100%' }}
                >
                  <Toast
                    tone={t.tone}
                    description={t.description}
                    action={t.action}
                    onAction={() => {
                      t.onAction?.();
                      dismiss(t.id);
                    }}
                  >
                    {t.message}
                  </Toast>
                </motion.div>
              ))}
            </AnimatePresence>
          </div>,
          document.body,
        )}
    </ToastCtx.Provider>
  );
}
