import { cx } from '../../utils/cx';
import type { ButtonStyleOptions } from './Button';
import './button.css';

export function buttonClass({ variant = 'primary', size = 'md', block, className }: ButtonStyleOptions = {}) {
  return cx('sm-btn', `sm-btn--${variant}`, size !== 'md' && `sm-btn--${size}`, block && 'sm-btn--block', className);
}
