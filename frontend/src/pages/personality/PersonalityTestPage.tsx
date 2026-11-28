import { ArrowRight } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { isBackendError, type BackendError } from '../../api/errors';
import { submitPersonalityTest } from '../../api/personalityTest';
import {
  Button,
  Card,
  PersonalityTest,
  ProfileBars,
  type PersonalityQuestion,
} from '../../studymatch-ui';
import type { PersonalityProfileData } from '../../types/personalityProfile';
import { toPercentages } from './personalityScores';
import './PersonalityTestPage.css';

const QUESTION_COUNT = 20;

export interface PersonalityTestPageProps {
  onCompleted: () => void;
  // a way to Help while the test isn't done (#363)
  help?: ReactNode;
}

export function PersonalityTestPage({ onCompleted, help }: PersonalityTestPageProps) {
  const { t } = useTranslation();
  const [profile, setProfile] = useState<PersonalityProfileData | null>(null);

  const questions: PersonalityQuestion[] = Array.from({ length: QUESTION_COUNT }, (_, index) => ({
    id: questionId(index),
    text: t(`personality.questions.${index + 1}`),
  }));

  async function submit(answers: Record<string, number>) {
    const answersInOrder = questions.map(question => answers[question.id] ?? null);
    try {
      setProfile(await submitPersonalityTest(answersInOrder));
    } catch (error) {
      throw isBackendError(error) ? withQuestionIds(error) : error;
    }
  }

  return (
    <main className="personality-page">
      {profile === null && help && <p className="personality-page__help">{help}</p>}
      {profile === null
        ? <PersonalityTest questions={questions} onSubmit={submit} />
        : <PersonalityResult profile={profile} onContinue={onCompleted} />}
    </main>
  );
}

function PersonalityResult({ profile, onContinue }: { profile: PersonalityProfileData; onContinue: () => void }) {
  const { t } = useTranslation();
  return (
    <Card padding="lg" className="sm-pt">
      <div className="personality-page__profile">
        <h2 className="sm-title">{t('personality.yourProfile')}</h2>
        <ProfileBars values={toPercentages(profile)} />
        <p className="sm-caption">{t('personality.privacy')}</p>
        <Button size="lg" iconRight={ArrowRight} onClick={onContinue}>{t('personality.continue')}</Button>
      </div>
    </Card>
  );
}

function questionId(index: number) {
  return `q${index + 1}`;
}

function withQuestionIds(error: BackendError): BackendError {
  return {
    ...error,
    fieldErrors: error.fieldErrors?.map(fieldError => {
      const position = /^answers\[(\d+)\]$/.exec(fieldError.field);
      return position === null ? fieldError : { ...fieldError, field: questionId(Number(position[1])) };
    }),
  };
}
