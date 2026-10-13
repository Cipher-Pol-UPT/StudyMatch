import { Minus, Plus } from 'lucide-react';
import { useId, type ReactNode } from 'react';
import { cx } from '../../utils/cx';
import { useControllableState } from '../../utils/shared';
import './inputs.css';

export interface NumberStepperProps {
  label: ReactNode;
  value?: number;
  defaultValue?: number;
  onValueChange?: (value: number) => void;
  min?: number;
  max?: number;
  step?: number;
  /** Text after the number ("students"). */
  unit?: ReactNode;
  hint?: ReactNode;
  disabled?: boolean;
  className?: string;
}

/** − value + control for small integers: group sizes, seats in a StudyRoom, weights. */
export function NumberStepper({ label, value, defaultValue = 0, onValueChange, min = -Infinity, max = Infinity, step = 1, unit, hint, disabled, className }: NumberStepperProps) {
  const id = useId();
  const [n, setN] = useControllableState(value, defaultValue, onValueChange);
  const clamp = (x: number) => Math.min(max, Math.max(min, x));
  return (
    <div className={cx('sm-stepper', disabled && 'sm-stepper--disabled', className)}>
      <div className="sm-stepper__text">
        <span className="sm-stepper__label" id={`${id}-label`}>
          {label}
        </span>
        {hint && <span className="sm-stepper__hint">{hint}</span>}
      </div>
      <div
        className="sm-stepper__control"
        role="spinbutton"
        tabIndex={disabled ? -1 : 0}
        aria-labelledby={`${id}-label`}
        aria-valuenow={n}
        aria-valuemin={Number.isFinite(min) ? min : undefined}
        aria-valuemax={Number.isFinite(max) ? max : undefined}
        aria-disabled={disabled || undefined}
        onKeyDown={(e) => {
          if (disabled) return;
          if (e.key === 'ArrowUp' || e.key === 'ArrowRight') {
            e.preventDefault();
            setN(clamp(n + step));
          } else if (e.key === 'ArrowDown' || e.key === 'ArrowLeft') {
            e.preventDefault();
            setN(clamp(n - step));
          } else if (e.key === 'Home' && Number.isFinite(min)) setN(min);
          else if (e.key === 'End' && Number.isFinite(max)) setN(max);
        }}
      >
        <button type="button" tabIndex={-1} aria-label="Decrease" disabled={disabled || n <= min} onClick={() => setN(clamp(n - step))}>
          <Minus size={16} strokeWidth={2.5} />
        </button>
        <span className="sm-stepper__value">
          {n}
          {unit && <small>{unit}</small>}
        </span>
        <button type="button" tabIndex={-1} aria-label="Increase" disabled={disabled || n >= max} onClick={() => setN(clamp(n + step))}>
          <Plus size={16} strokeWidth={2.5} />
        </button>
      </div>
    </div>
  );
}
