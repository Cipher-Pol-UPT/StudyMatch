import { AnimatePresence,motion } from 'framer-motion';
import { cloneElement,isValidElement,useId,useRef,useState,type ReactElement,type ReactNode } from 'react';
import { EASE_OUT } from '../../utils/shared';
import './overlay.css';

export interface TooltipProps {
  content: ReactNode;
  /** A single focusable element (button, link…). It receives `aria-describedby`. */
  children: ReactElement;
  side?: 'top' | 'bottom';
  /** Hover delay in ms. Focus shows the tooltip immediately. */
  delay?: number;
}

/** Short, non-essential hint on hover and focus. Never put the only copy of important info here. */
export function Tooltip({ content, children, side = 'top', delay = 250 }: TooltipProps) {
  const [open, setOpen] = useState(false);
  const id = useId();
  const timer = useRef<number | undefined>(undefined);
  const show = (now?: boolean) => {
    window.clearTimeout(timer.current);
    if (now) setOpen(true);
    else timer.current = window.setTimeout(() => setOpen(true), delay);
  };
  const hide = () => {
    window.clearTimeout(timer.current);
    setOpen(false);
  };
  const child = isValidElement(children) ? cloneElement(children as ReactElement<Record<string, unknown>>, { 'aria-describedby': open ? id : undefined }) : children;
  return (
    <span
      className="sm-tip-anchor"
      onMouseEnter={() => show()}
      onMouseLeave={hide}
      onFocus={() => show(true)}
      onBlur={hide}
      onKeyDown={(e) => e.key === 'Escape' && hide()}
    >
      {child}
      <AnimatePresence>
        {open && (
          <motion.span
            id={id}
            role="tooltip"
            className={`sm-tip sm-tip--${side}`}
            style={{ x: '-50%' }}
            initial={{ opacity: 0, y: side === 'top' ? 6 : -6, scale: 0.92 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, scale: 0.96 }}
            transition={{ duration: 0.2, ease: EASE_OUT }}
          >
            {content}
          </motion.span>
        )}
      </AnimatePresence>
    </span>
  );
}
