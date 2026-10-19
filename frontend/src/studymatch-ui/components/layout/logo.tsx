import { cx } from '../../utils/cx';

export function Logo({ className }: { className?: string }) {
  return (
    <div 
      className={cx("sm-logo", className)}
      style={{
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
        gap: '4px',
        background: 'var(--sm-black)',
        padding: '8px',
        borderRadius: 'var(--sm-r-sm)',
        width: '40px',
        height: '40px'
      }}
      aria-label="StudyMatch Logo"
    >
      <div style={{ background: '#8b5cf6', borderRadius: '50%' }} /> 
      <div style={{ background: '#2a9fd6', borderRadius: '50%' }} /> 
      <div style={{ background: '#eb6834', borderRadius: '50%' }} /> 
      <div style={{ background: '#1baf7a', borderRadius: '50%' }} /> 
    </div>
  );
}