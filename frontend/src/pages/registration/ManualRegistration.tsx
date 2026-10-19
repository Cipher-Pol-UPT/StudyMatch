import { CheckCircle2, MailWarning, RefreshCw, UserPlus } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { listDegrees } from '../../api/degrees';
import { registerStudent } from '../../api/students';
import { Banner, Button, Card, EmptyState, RegisterStudentForm, Spinner } from '../../studymatch-ui';
import type { DegreeData, RegisteredStudent, StudentRegistration } from '../../types/registration';
import { ResendCredentials } from './ResendCredentials';

export function ManualRegistration() {
  const { t } = useTranslation();
  const [degrees, setDegrees] = useState<DegreeData[] | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [registered, setRegistered] = useState<{ student: RegisteredStudent; personalEmail: string } | null>(null);
  const [formKey, setFormKey] = useState(0);

  useEffect(() => {
    let isCurrentAttempt = true;
    listDegrees()
      .then(loaded => isCurrentAttempt && setDegrees(loaded))
      .catch(() => isCurrentAttempt && setLoadFailed(true));
    return () => {
      isCurrentAttempt = false;
    };
  }, [loadAttempt]);

  async function register(student: StudentRegistration) {
    setRegistered({ student: await registerStudent(student), personalEmail: student.personalEmail });
  }

  function registerAnother() {
    setRegistered(null);
    setFormKey(key => key + 1);
  }

  if (loadFailed) {
    return (
      <EmptyState
        icon={RefreshCw}
        title={t('common.unexpectedError')}
        action={<Button variant="secondary" onClick={() => { setLoadFailed(false); setLoadAttempt(attempt => attempt + 1); }}>{t('profile.tryAgain')}</Button>}
      />
    );
  }

  if (degrees === null) {
    return <div className="registration__loading"><Spinner /></div>;
  }

  if (registered) {
    const { student, personalEmail } = registered;
    return (
      <Card className="registration__result">
        {student.emailSent
          ? <CheckCircle2 className="registration__result-icon" aria-hidden />
          : <MailWarning className="registration__result-icon registration__result-icon--warning" aria-hidden />}
        <h2>{t('registration.registered')}</h2>
        <dl className="registration__details">
          <div><dt>{t('fields.name')}</dt><dd>{student.name}</dd></div>
          <div><dt>{t('fields.studentNumber')}</dt><dd className="sm-num">{student.number}</dd></div>
          <div><dt>{t('fields.institutionalEmail')}</dt><dd>{student.institutionalEmail}</dd></div>
          <div><dt>{t('fields.degreeProgramme')}</dt><dd>{t('registration.degreeAndYear', { degree: student.degreeProgramme, year: student.curricularYear })}</dd></div>
        </dl>
        {student.emailSent ? (
          <p className="registration__note">{t('registration.credentialsSent')}</p>
        ) : (
          <>
            <Banner tone="warning">{t('registration.emailNotSent')}</Banner>
            <ResendCredentials studentNumber={student.number} personalEmail={personalEmail} />
          </>
        )}
        <Button icon={UserPlus} onClick={registerAnother}>{t('registration.registerAnother')}</Button>
      </Card>
    );
  }

  return (
    <Card className="registration__card">
      <RegisterStudentForm key={formKey} degrees={degrees} onSubmit={register} />
    </Card>
  );
}
