import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Tabs } from '../../studymatch-ui';
import { CsvImport } from './CsvImport';
import { ManualRegistration } from './ManualRegistration';
import './registration.css';

type RegistrationMode = 'manual' | 'csv';

export function RegisterStudentPage() {
  const { t } = useTranslation();
  const [mode, setMode] = useState<RegistrationMode>('manual');

  return (
    <div className="registration">
      <header className="registration__head">
        <h1>{t('nav.registerStudent')}</h1>
        <p>{t('registration.intro')}</p>
      </header>
      <Tabs<RegistrationMode>
        label={t('nav.registerStudent')}
        value={mode}
        onValueChange={setMode}
        tabs={[{ value: 'manual', label: t('registration.oneStudent') }, { value: 'csv', label: t('registration.csvImport') }]}
      />
      {mode === 'manual' ? <ManualRegistration /> : <CsvImport />}
    </div>
  );
}
