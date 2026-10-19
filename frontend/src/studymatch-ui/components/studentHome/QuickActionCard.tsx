import type { ReactNode } from 'react';
import { ArrowRight } from 'lucide-react';
import { Card } from '../card/Card';

export interface QuickActionCardProps {
  title: string;
  subtitle: string;
  icon: ReactNode;
  color: string;
  actionLabel: string;
  onAction?: () => void;
}

export function QuickActionCard({ title, subtitle, icon, color, actionLabel, onAction }: QuickActionCardProps) {
  return (
    <Card as="article" className="sh-qa">
      <div className="sh-qa__tile" style={{ background: `color-mix(in srgb, ${color} 30%, white)` }} aria-hidden>
        {icon}
      </div>
      <h3 className="sh-qa__title">{title}</h3>
      <p className="sh-qa__desc">{subtitle}</p>
      <button className="sh-qa__btn" style={{ background: color }} onClick={onAction}>
        <span>{actionLabel}</span>
        <ArrowRight size={16} aria-hidden />
      </button>
    </Card>
  );
}