import { CheckCircle2, UserPlus } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { createAccount } from '../../api/accounts';
import { Button, Card, CreateAccountForm } from '../../studymatch-ui';
import type { CreatedAccount } from '../../types/accounts';
import type { CreateAccountInput } from '../../types/forms';
import '../registration/registration.css';

export interface CreateAccountPageProps {
  domain: string;
}

export function CreateAccountPage({ domain }: CreateAccountPageProps) {
  const { t } = useTranslation();
  const [created, setCreated] = useState<CreatedAccount | null>(null);
  const [formKey, setFormKey] = useState(0);

  async function create(account: CreateAccountInput) {
    setCreated(await createAccount(account));
  }

  function createAnother() {
    setCreated(null);
    setFormKey(key => key + 1);
  }

  return (
    <div className="registration">
      <header className="registration__head">
        <h1>{t('workspace.sections.createAccount')}</h1>
        <p>{t('accounts.intro')}</p>
      </header>
      {created ? (
        <Card className="registration__result">
          <CheckCircle2 className="registration__result-icon" aria-hidden />
          <h2>{t('accounts.created', { type: t(`roles.${created.type}`) })}</h2>
          <dl className="registration__details">
            <div><dt>{t('fields.name')}</dt><dd>{created.name}</dd></div>
            <div><dt>{t('accounts.employeeNumber')}</dt><dd className="sm-num">{created.number}</dd></div>
            <div><dt>{t('fields.institutionalEmail')}</dt><dd>{created.institutionalEmail}</dd></div>
          </dl>
          <p className="registration__note">{t('accounts.credentialsSent')}</p>
          <Button icon={UserPlus} onClick={createAnother}>{t('accounts.createAnother')}</Button>
        </Card>
      ) : (
        <Card className="registration__card">
          <CreateAccountForm key={formKey} domain={domain} onSubmit={create} />
        </Card>
      )}
    </div>
  );
}
