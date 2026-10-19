import { AnimatePresence,motion } from 'framer-motion';
import { ChevronDown } from 'lucide-react';
import { useId,useState,type ReactNode } from 'react';
import { cx } from '../../utils/cx';
import { EASE_OUT } from '../../utils/shared';
import './accordion.css';

export interface AccordionItem {
  id: string;
  title: ReactNode;
  meta?: ReactNode;
  content: ReactNode;
  disabled?: boolean;
}

export interface AccordionProps {
  items: AccordionItem[];
  multiple?: boolean;
  defaultOpen?: string[];
  variant?: 'card' | 'plain';
  className?: string;
}

export function Accordion({ items, multiple, defaultOpen = [], variant = 'card', className }: AccordionProps) {
  const base = useId();
  const [open, setOpen] = useState<string[]>(defaultOpen);
  const toggle = (id: string) =>
    setOpen((cur) => (cur.includes(id) ? cur.filter((x) => x !== id) : multiple ? [...cur, id] : [id]));
  return (
    <div className={cx('sm-accordion', variant === 'plain' && 'sm-accordion--plain', className)}>
      {items.map((it) => {
        const isOpen = open.includes(it.id);
        const hid = `${base}-h-${it.id}`;
        const pid = `${base}-p-${it.id}`;
        return (
          <div key={it.id} className="sm-accordion__item" data-open={isOpen}>
            <h3 className="sm-accordion__heading">
              <button type="button" id={hid} aria-expanded={isOpen} aria-controls={pid} disabled={it.disabled} onClick={() => toggle(it.id)} className="sm-accordion__trigger">
                <span className="sm-accordion__title">{it.title}</span>
                {it.meta && <span className="sm-accordion__meta">{it.meta}</span>}
                <ChevronDown className="sm-accordion__chev" size={20} strokeWidth={2.25} aria-hidden />
              </button>
            </h3>
            <AnimatePresence initial={false}>
              {isOpen && (
                <motion.div
                  id={pid}
                  role="region"
                  aria-labelledby={hid}
                  initial={{ height: 0, opacity: 0 }}
                  animate={{ height: 'auto', opacity: 1 }}
                  exit={{ height: 0, opacity: 0 }}
                  transition={{ duration: 0.35, ease: EASE_OUT }}
                  style={{ overflow: 'hidden' }}
                >
                  <div className="sm-accordion__panel">{it.content}</div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        );
      })}
    </div>
  );
}
