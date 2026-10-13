import { forwardRef,useId,type ButtonHTMLAttributes,type ReactNode } from 'react';
import { cx } from '../../utils/cx';
import { useControllableState } from '../../utils/shared';
import './selection.css';

export interface SwitchProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'onChange' | 'value' | 'defaultValue'> {
  checked?: boolean;
  defaultChecked?: boolean;
  onCheckedChange?: (checked: boolean) => void;
  /** Accessible name when there is no visible `children` label. */
  label?: string;
  /** Visible label rendered next to the switch. */
  children?: ReactNode;
  size?: 'sm' | 'md';
}

/** iOS-style on/off switch for settings that apply immediately. */
export const Switch = forwardRef<HTMLButtonElement, SwitchProps>(function Switch(
  { checked, defaultChecked = false, onCheckedChange, label, children, size = 'md', className, disabled, onClick, ...rest },
  ref,
) {
  const [on, setOn] = useControllableState(checked, defaultChecked, onCheckedChange);
  const labelId = useId();
  const control = (
    <button
      ref={ref}
      type="button"
      role="switch"
      aria-checked={on}
      aria-label={children ? undefined : label}
      aria-labelledby={children ? labelId : undefined}
      className={cx('sm-switch', size === 'sm' && 'sm-switch--sm', className)}
      disabled={disabled}
      onClick={(e) => {
        setOn(!on);
        onClick?.(e);
      }}
      {...rest}
    >
      <i />
    </button>
  );
  if (!children) return control;
  return (
    <span className="sm-switch-label">
      {control}
      <span id={labelId} onClick={() => !disabled && setOn(!on)}>
        {children}
      </span>
    </span>
  );
});

export interface ControlRowProps {
  title: ReactNode;
  description?: ReactNode;
  children: ReactNode;
  className?: string;
}

/** A settings row: title and description on the left, a control on the right. */
export function ControlRow({ title, description, children, className }: ControlRowProps) {
  return (
    <div className={cx('sm-control-row', className)}>
      <div className="sm-control-row__text">
        <div className="sm-control-row__title">{title}</div>
        {description && <div className="sm-control-row__desc">{description}</div>}
      </div>
      {children}
    </div>
  );
}

export const Toggle = Switch;
