import { forwardRef, type ButtonHTMLAttributes, type HTMLAttributes } from 'react';
import { cx } from '../../utils/cx';
import type { IconComponent } from '../../utils/shared';
import './button.css';

export type ButtonVariant =
  | 'primary'
  | 'gradient'
  | 'tinted'
  | 'secondary'
  | 'black'
  | 'white'
  | 'outline'
  | 'ghost'
  | 'danger'
  | 'danger-solid';
export type ButtonSize = 'sm' | 'md' | 'lg' | 'xl';

export interface ButtonStyleOptions {
  variant?: ButtonVariant;
  size?: ButtonSize;
  block?: boolean;
  className?: string;
}

/**
 * Class names for anything that should look like a button,
 * e.g. a router link: `<Link className={buttonClass({ variant: 'white' })} />`.
 */
export function buttonClass({ variant = 'primary', size = 'md', block, className }: ButtonStyleOptions = {}) {
  return cx('sm-btn', `sm-btn--${variant}`, size !== 'md' && `sm-btn--${size}`, block && 'sm-btn--block', className);
}

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** Visual weight. `primary` is reserved for the single most important action on a screen. */
  variant?: ButtonVariant;
  size?: ButtonSize;
  /** Icon before the label. */
  icon?: IconComponent;
  /** Icon after the label (e.g. an arrow). */
  iconRight?: IconComponent;
  /** Stretch to the container width. */
  block?: boolean;
  /** Shows a spinner, keeps the width and blocks clicks. */
  loading?: boolean;
}

const ICON_SIZE: Record<ButtonSize, number> = { sm: 16, md: 18, lg: 20, xl: 22 };

/** Pill button. Defaults to `type="button"` so it never submits a form by accident. */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', icon: Icon, iconRight: IconRight, block, loading, disabled, className, children, type = 'button', ...rest },
  ref,
) {
  const s = ICON_SIZE[size];
  return (
    <button
      ref={ref}
      type={type}
      className={cx(buttonClass({ variant, size, block }), loading && 'sm-btn--loading', className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading && <span className="sm-btn__spin" aria-hidden />}
      {Icon && <Icon size={s} strokeWidth={2.25} aria-hidden />}
      {children != null && children !== false && <span className="sm-btn__label">{children}</span>}
      {IconRight && <IconRight size={s} strokeWidth={2.25} aria-hidden />}
    </button>
  );
});

export type IconButtonVariant = 'secondary' | 'primary' | 'tinted' | 'black' | 'white' | 'ghost';

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon: IconComponent;
  /** Accessible name. Also shown as a native tooltip. */
  label: string;
  variant?: IconButtonVariant;
  size?: 'sm' | 'md' | 'lg';
}

/** Circular, icon-only button. `label` is required for screen readers. */
export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(function IconButton(
  { icon: Icon, label, variant = 'secondary', size = 'md', className, type = 'button', ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      aria-label={label}
      title={label}
      className={cx('sm-icon-btn', `sm-icon-btn--${variant}`, size !== 'md' && `sm-icon-btn--${size}`, className)}
      {...rest}
    >
      <Icon size={size === 'sm' ? 16 : size === 'lg' ? 24 : 20} strokeWidth={2.25} aria-hidden />
    </button>
  );
});

export interface ButtonGroupProps extends HTMLAttributes<HTMLDivElement> {
  /** Stack vertically (mobile footers, sheets). */
  stack?: boolean;
  /** Align to the end (dialog footers). */
  end?: boolean;
}

export function ButtonGroup({ stack, end, className, ...rest }: ButtonGroupProps) {
  return <div className={cx('sm-btn-group', stack && 'sm-btn-group--stack', end && 'sm-btn-group--end', className)} {...rest} />;
}
