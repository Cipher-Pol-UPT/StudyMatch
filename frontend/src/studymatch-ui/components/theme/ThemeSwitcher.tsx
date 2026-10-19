import { Palette } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, IconButton } from '../button/Button';
import { Dialog } from '../overlay/Dialog';
import { ThemeSelector } from './ThemeSelector';

export function ThemeSwitcher() {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  return (
    <>
      <IconButton icon={Palette} label={t('appearance.title')} variant="ghost" size="sm" onClick={() => setOpen(true)} />
      <Dialog open={open} onClose={() => setOpen(false)} title={t('appearance.title')} description={t('appearance.description')} size="lg" className="sm-theme-dialog" footer={<Button onClick={() => setOpen(false)}>{t('appearance.done')}</Button>}>
        <ThemeSelector />
      </Dialog>
    </>
  );
}
