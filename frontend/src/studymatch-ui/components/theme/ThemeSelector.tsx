import { Check, Moon, Sun } from 'lucide-react';
import { useRef, type CSSProperties, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../../themes/ThemeContext';
import { THEMES } from '../../themes/themes';
import './theme.css';

export function ThemeSelector() {
  const { t } = useTranslation();
  const { theme, setTheme } = useTheme();
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);

  const navigate = (event: KeyboardEvent, index: number) => {
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? THEMES.length - 1
      : event.key === 'ArrowRight' || event.key === 'ArrowDown' ? (index + 1) % THEMES.length
        : event.key === 'ArrowLeft' || event.key === 'ArrowUp' ? (index - 1 + THEMES.length) % THEMES.length : -1;
    if (next === -1) return;
    event.preventDefault();
    setTheme(THEMES[next].id);
    buttons.current[next]?.focus();
  };

  return (
    <div className="sm-theme-selector" role="radiogroup" aria-label={t('appearance.chooseTheme')}>
      {THEMES.map((definition, index) => {
        const selected = theme === definition.id;
        const ModeIcon = definition.mode === 'dark' ? Moon : Sun;
        return (
          <button
            key={definition.id}
            ref={(node) => { buttons.current[index] = node; }}
            type="button"
            className="sm-theme-selector__option"
            role="radio"
            aria-checked={selected}
            aria-label={`${definition.name}, ${t(`appearance.modes.${definition.mode}`)}`}
            tabIndex={selected ? 0 : -1}
            data-autofocus={selected ? '' : undefined}
            onClick={() => setTheme(definition.id)}
            onKeyDown={(event) => navigate(event, index)}
          >
            <span className="sm-theme-selector__preview" aria-hidden style={{
              '--preview-canvas': definition.swatches.canvas,
              '--preview-surface': definition.swatches.surface,
              '--preview-accent': definition.swatches.accent,
              '--preview-spark': definition.swatches.spark,
              '--preview-text': definition.swatches.text,
            } as CSSProperties}>
              <span className="sm-theme-selector__mini-nav"><i /><b /><em /></span>
              <span className="sm-theme-selector__mini-card"><b /><i /><span><em /><small /></span></span>
            </span>
            <span className="sm-theme-selector__name">{definition.name}{selected && <Check size={16} aria-hidden />}</span>
            <span className="sm-theme-selector__mode"><ModeIcon size={12} aria-hidden />{t(`appearance.modes.${definition.mode}`)}</span>
            <span className="sm-theme-selector__description">{t(`appearance.descriptions.${definition.id}`)}</span>
          </button>
        );
      })}
    </div>
  );
}
