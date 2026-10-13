import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { FormFeedbackProps } from '../../../types/forms';
import { Button } from '../../components/button/Button';
import { Card } from '../../components/card/Card';
import { Banner } from '../../components/overlay/Banner';
import { useFormFeedback } from '../../utils/useFormFeedback';
import './personality.css';
export interface PersonalityQuestion { id: string; text: string }
export interface PersonalityTestProps extends FormFeedbackProps {
  questions: PersonalityQuestion[]; onSubmit: (answers: Record<string, number>) => Promise<void>;
}
export function PersonalityTest({ questions, onSubmit, fieldErrors }: PersonalityTestProps) {
  const { t } = useTranslation(); const [answers, setAnswers] = useState<Record<string, number>>({});
  const feedback = useFormFeedback(fieldErrors);
  return <Card><form noValidate onSubmit={event => { event.preventDefault(); void feedback.submit(() => onSubmit(answers)); }}>
    <h2>{t('personality.title')}</h2>
    {questions.map(question => <fieldset key={question.id} disabled={feedback.busy}>
      <legend>{question.text}</legend>
      {[1,2,3,4,5].map(value => <label key={value}>
        <input type="radio" name={question.id} checked={answers[question.id] === value}
          onChange={() => setAnswers(a => ({ ...a, [question.id]: value }))} />{t(`likert.${value}`)}
      </label>)}
      {feedback.fieldError(question.id) && <p role="alert">{feedback.fieldError(question.id)}</p>}
    </fieldset>)}
    {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
    <Button type="submit" loading={feedback.busy}>{t('common.submit')}</Button>
  </form></Card>;
}
