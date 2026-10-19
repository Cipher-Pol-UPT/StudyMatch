import { ArrowRight } from 'lucide-react';
import { useState, type CSSProperties } from 'react';
import { useTranslation } from 'react-i18next';
import { isBackendError, type BackendError } from '../../api/errors';
import { submitPersonalityTest } from '../../api/personalityTest';
import {
  Button,
  Card,
  PersonalityTest,
  PROFILE_KEYS,
  PROFILES,
  ProfileBars,
  type PersonalityQuestion,
  type ProfileKey,
} from '../../studymatch-ui';
import type { PersonalityProfileData } from '../../types/personalityProfile';
import './PersonalityTestPage.css';

const QUESTION_COUNT = 15;

export interface PersonalityTestPageProps {
  onCompleted: () => void;
}

export function PersonalityTestPage({ onCompleted }: PersonalityTestPageProps) {
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
      {profile === null
        ? <PersonalityTest questions={questions} onSubmit={submit} />
        : <PersonalityResult profile={profile} onContinue={onCompleted} />}
    </main>
  );
}

function PersonalityResult({ profile, onContinue }: { profile: PersonalityProfileData; onContinue: () => void }) {
  const { t } = useTranslation();
  const percentages = toPercentages(profile);
  const strongest = PROFILE_KEYS.reduce((best, key) => (percentages[key] > percentages[best] ? key : best));
  const { label, description, color, icon: Icon } = PROFILES[strongest];

  return (
    <Card padding="lg" className="sm-pt">
      <div className="sm-pt__result">
        <div className="sm-pt__hero personality-page__hero" style={{ '--profile-color': color } as CSSProperties}>
          <span className="sm-pt__hero-icon" style={{ background: color }}>
            <Icon size={30} strokeWidth={2.25} aria-hidden />
          </span>
          <span className="sm-eyebrow">{t('personality.strongestProfile')}</span>
          <h2 className="sm-h2">{label}</h2>
          <p>{description}</p>
        </div>
        <div className="personality-page__profile">
          <h3 className="sm-title">{t('personality.yourProfile')}</h3>
          <ProfileBars values={percentages} />
          <p className="sm-caption">{t('personality.privacy')}</p>
          <Button size="lg" iconRight={ArrowRight} onClick={onContinue}>{t('personality.continue')}</Button>
        </div>
      </div>
    </Card>
  );
}

function questionId(index: number) {
  return `q${index + 1}`;
}

function toPercentages(profile: PersonalityProfileData): Record<ProfileKey, number> {
  return {
    leadership: profile.leadership * 100,
    technical: profile.problemSolving * 100,
    creativity: profile.creativity * 100,
    execution: profile.execution * 100,
  };
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
