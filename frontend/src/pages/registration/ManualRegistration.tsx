import { CheckCircle2, MailWarning, UserPlus } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getDegrees } from '../../api/degrees';
import { registerStudent } from '../../api/students';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Banner, Button, Card, RegisterStudentForm } from '../../studymatch-ui';
import type { RegisteredStudent, StudentRegistration } from '../../types/registration';
import { ResendCredentials } from './ResendCredentials';

export function ManualRegistration() {
  const { t } = useTranslation();
  const degrees = useRemoteData('degrees', getDegrees);
  const [registered, setRegistered] = useState<{ student: RegisteredStudent; personalEmail: string } | null>(null);
  const [formKey, setFormKey] = useState(0);

  async function register(student: StudentRegistration) {
    setRegistered({ student: await registerStudent(student), personalEmail: student.personalEmail });
  }

  function registerAnother() {
    setRegistered(null);
    setFormKey(key => key + 1);
  }

  if (!degrees.data) {
    return <DataLoadState error={degrees.error} retry={degrees.retry} />;
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
      <RegisterStudentForm key={formKey} degrees={degrees.data} onSubmit={register} />
    </Card>
  );
}
