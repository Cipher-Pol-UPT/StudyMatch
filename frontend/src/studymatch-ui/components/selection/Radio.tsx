import { createContext,forwardRef,useContext,useId,type InputHTMLAttributes,type ReactNode } from 'react';
import { cx } from '../../utils/cx';
import { useControllableState } from '../../utils/shared';
import './selection.css';

interface RadioGroupCtx {
  name: string;
  value: string;
  setValue: (v: string) => void;
  card: boolean;
  disabled?: boolean;
}
const Ctx = createContext<RadioGroupCtx | null>(null);

export interface RadioProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'children' | 'value'> {
  value: string;
  children?: ReactNode;
  description?: ReactNode;
}

/** A single radio. Inside a <RadioGroup> it reads name and checked state from the group. */
export const Radio = forwardRef<HTMLInputElement, RadioProps>(function Radio({ value, children, description, className, disabled, onChange, ...rest }, ref) {
  const group = useContext(Ctx);
  const isDisabled = disabled ?? group?.disabled;
  return (
    <label className={cx('sm-radio', group?.card && 'sm-radio--card', isDisabled && 'sm-radio--disabled', className)}>
      <input
        ref={ref}
        type="radio"
        value={value}
        name={group?.name ?? rest.name}
        checked={group ? group.value === value : rest.checked}
        disabled={isDisabled}
        onChange={(e) => {
          group?.setValue(value);
          onChange?.(e);
        }}
        {...rest}
      />
      <span className="sm-radio__dot" aria-hidden />
      {(children || description) && (
        <span className="sm-choice__text">
          {children}
          {description && <span className="sm-choice__desc">{description}</span>}
        </span>
      )}
    </label>
  );
});

export interface RadioGroupProps {
  /** Visible group label (rendered as a <legend>). */
  label?: ReactNode;
  name?: string;
  value?: string;
  defaultValue?: string;
  onValueChange?: (value: string) => void;
  /** Options as data. You can also pass <Radio> children. */
  options?: { value: string; label: ReactNode; description?: ReactNode; disabled?: boolean }[];
  orientation?: 'vertical' | 'horizontal';
  /** Render options as big tappable cards. */
  variant?: 'default' | 'cards';
  disabled?: boolean;
  className?: string;
  children?: ReactNode;
}

export function RadioGroup({ label, name, value, defaultValue = '', onValueChange, options, orientation = 'vertical', variant = 'default', disabled, className, children }: RadioGroupProps) {
  const auto = useId();
  const [current, setCurrent] = useControllableState(value, defaultValue, onValueChange);
  return (
    <Ctx.Provider value={{ name: name ?? auto, value: current, setValue: setCurrent, card: variant === 'cards', disabled }}>
      <fieldset
        className={cx('sm-radio-group', orientation === 'horizontal' && 'sm-radio-group--row', variant === 'cards' && 'sm-radio-group--cards', className)}
        role="radiogroup"
      >
        {label && <legend>{label}</legend>}
        {options?.map((o) => (
          <Radio key={o.value} value={o.value} description={o.description} disabled={o.disabled}>
            {o.label}
          </Radio>
        ))}
        {children}
      </fieldset>
    </Ctx.Provider>
  );
}
