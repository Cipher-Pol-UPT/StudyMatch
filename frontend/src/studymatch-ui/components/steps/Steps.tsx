import { Check } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import './steps.css';

export interface StepItem {
  label: ReactNode;
  description?: ReactNode;
}

export interface StepsProps {
  steps: (StepItem | string)[];
  current: number;
  orientation?: 'horizontal' | 'vertical';
  onStepClick?: (index: number) => void;
  className?: string;
}

export function Steps({ steps, current, orientation = 'horizontal', onStepClick, className }: StepsProps) {
  useTranslation();
  return (
    <ol className={cx('sm-steps', orientation === 'vertical' && 'sm-steps--vertical', className)}>
      {steps.map((raw, i) => {
        const s = typeof raw === 'string' ? { label: raw } : raw;
        const state = i < current ? 'done' : i === current ? 'current' : 'todo';
        const clickable = onStepClick && i < current;
        const inner = (
          <>
            <span className="sm-steps__dot" aria-hidden>
              {state === 'done' ? <Check size={14} strokeWidth={3} /> : i + 1}
            </span>
            <span className="sm-steps__text">
              <span className="sm-steps__label">{s.label}</span>
              {s.description && <span className="sm-steps__desc">{s.description}</span>}
            </span>
          </>
        );
        return (
          <li key={i} className="sm-steps__item" data-state={state} aria-current={state === 'current' ? 'step' : undefined}>
            {clickable ? (
              <button type="button" className="sm-steps__btn" onClick={() => onStepClick(i)}>
                {inner}
              </button>
            ) : (
              <span className="sm-steps__btn">{inner}</span>
            )}
            <span className="sm-visually-hidden">{state === 'done' ? '(completed)' : state === 'current' ? i18n.t('ui.current_step') : ''}</span>
          </li>
        );
      })}
    </ol>
  );
}
