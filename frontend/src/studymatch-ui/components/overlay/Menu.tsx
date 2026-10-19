import { useEffect,useRef,type KeyboardEvent,type ReactElement,type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import { useControllableState,type IconComponent } from '../../utils/shared';
import { Popover } from './Popover';

export type MenuEntry =
  | {
      type?: 'item';
      label: ReactNode;
      icon?: IconComponent;
      onSelect?: () => void;
      danger?: boolean;
      disabled?: boolean;
      shortcut?: string;
    }
  | { type: 'separator' }
  | { type: 'label'; label: ReactNode };

export interface MenuProps {
  trigger: ReactElement;
  items: MenuEntry[];
  align?: 'start' | 'end';
  side?: 'bottom' | 'top';
  label?: string;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  width?: number;
}

export function Menu({ trigger, items, align = 'end', side = 'bottom', label = i18n.t('ui.actions'), open, onOpenChange, width }: MenuProps) {
  useTranslation();
  const [isOpen, setOpen] = useControllableState(open, false, onOpenChange);
  const list = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isOpen) return;
    const raf = requestAnimationFrame(() => list.current?.querySelector<HTMLButtonElement>('[role="menuitem"]:not(:disabled)')?.focus());
    return () => cancelAnimationFrame(raf);
  }, [isOpen]);

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const els = Array.from(list.current?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]:not(:disabled)') ?? []);
    const i = els.indexOf(document.activeElement as HTMLButtonElement);
    let next = -1;
    if (e.key === 'ArrowDown') next = (i + 1) % els.length;
    else if (e.key === 'ArrowUp') next = (i - 1 + els.length) % els.length;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = els.length - 1;
    else if (e.key === 'Tab') setOpen(false);
    if (next >= 0) {
      e.preventDefault();
      els[next]?.focus();
    }
  };

  return (
    <Popover trigger={trigger} open={isOpen} onOpenChange={setOpen} role="menu" label={label} align={align} side={side} width={width}>
      {(close) => (
        <div ref={list} className="sm-menu" onKeyDown={onKeyDown}>
          {items.map((it, i) => {
            if (it.type === 'separator') return <div key={i} className="sm-menu__sep" role="separator" />;
            if (it.type === 'label') return <div key={i} className="sm-menu__label">{it.label}</div>;
            const Icon = it.icon;
            return (
              <button
                key={i}
                type="button"
                role="menuitem"
                tabIndex={-1}
                className={cx('sm-menu__item', it.danger && 'sm-menu__item--danger')}
                disabled={it.disabled}
                onClick={() => {
                  close();
                  it.onSelect?.();
                }}
              >
                {Icon && <Icon size={17} strokeWidth={2} aria-hidden />}
                {it.label}
                {it.shortcut && <span className="sm-menu__shortcut">{it.shortcut}</span>}
              </button>
            );
          })}
        </div>
      )}
    </Popover>
  );
}
