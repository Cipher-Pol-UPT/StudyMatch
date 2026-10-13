import { AnimatePresence,motion } from 'framer-motion';
import { cloneElement,isValidElement,useEffect,useId,useRef,type CSSProperties,type MouseEvent,type ReactElement,type ReactNode } from 'react';
import { cx } from '../../utils/cx';
import { EASE_OUT,useControllableState } from '../../utils/shared';
import './overlay.css';

export interface PopoverProps {
  /** The trigger. Must be a single element that accepts onClick (e.g. a Button or IconButton). */
  trigger: ReactElement;
  /** Content, or a render function that receives `close`. */
  children: ReactNode | ((close: () => void) => ReactNode);
  open?: boolean;
  defaultOpen?: boolean;
  onOpenChange?: (open: boolean) => void;
  side?: 'bottom' | 'top';
  align?: 'start' | 'end' | 'center';
  /** ARIA role of the panel. Menu sets this to "menu". */
  role?: string;
  /** Accessible name of the panel. */
  label?: string;
  width?: CSSProperties['width'];
  className?: string;
}

/** Click-to-open floating panel. Closes on outside click and Escape, returning focus to the trigger. */
export function Popover({ trigger, children, open, defaultOpen = false, onOpenChange, side = 'bottom', align = 'start', role = 'dialog', label, width, className }: PopoverProps) {
  const [isOpen, setOpen] = useControllableState(open, defaultOpen, onOpenChange);
  const anchor = useRef<HTMLSpanElement>(null);
  const id = useId();
  const close = () => setOpen(false);

  useEffect(() => {
    if (!isOpen) return;
    const onDown = (e: PointerEvent) => {
      if (!anchor.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
        (anchor.current?.querySelector('[aria-haspopup]') as HTMLElement | null)?.focus();
      }
    };
    document.addEventListener('pointerdown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [isOpen, setOpen]);

  const triggerEl = isValidElement(trigger)
    ? cloneElement(trigger as ReactElement<Record<string, unknown>>, {
        'aria-haspopup': role === 'menu' ? 'menu' : 'dialog',
        'aria-expanded': isOpen,
        'aria-controls': isOpen ? id : undefined,
        onClick: (e: MouseEvent) => {
          (trigger.props as { onClick?: (e: MouseEvent) => void }).onClick?.(e);
          setOpen(!isOpen);
        },
      })
    : trigger;

  return (
    <span ref={anchor} className="sm-popover-anchor">
      {triggerEl}
      <AnimatePresence>
        {isOpen && (
          <motion.div
            id={id}
            role={role}
            aria-label={label}
            className={cx('sm-popover', `sm-popover--${side}`, `sm-popover--${align}`, className)}
            style={{ width, x: align === 'center' ? '-50%' : 0, transformOrigin: `${align === 'end' ? 'right' : align === 'start' ? 'left' : 'center'} ${side === 'bottom' ? 'top' : 'bottom'}` }}
            initial={{ opacity: 0, scale: 0.94, y: side === 'bottom' ? -6 : 6 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.97 }}
            transition={{ duration: 0.22, ease: EASE_OUT }}
          >
            {typeof children === 'function' ? children(close) : children}
          </motion.div>
        )}
      </AnimatePresence>
    </span>
  );
}
