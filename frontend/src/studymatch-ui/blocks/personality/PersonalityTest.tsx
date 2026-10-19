import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { FormFeedbackProps } from '../../../types/forms';
import { Button } from '../../components/button/Button';
import { Card } from '../../components/card/Card';
import { Banner } from '../../components/overlay/Banner';
import { ProgressBar } from '../../components/progress/Progress';
import { cx } from '../../utils/cx';
import { useFormFeedback } from '../../utils/useFormFeedback';
import './personality.css';

const LIKERT_VALUES = [1, 2, 3, 4, 5];

export interface PersonalityQuestion { id: string; text: string }
export interface PersonalityTestProps extends FormFeedbackProps {
  questions: PersonalityQuestion[]; onSubmit: (answers: Record<string, number>) => Promise<void>;
}

export function PersonalityTest({ questions, onSubmit, fieldErrors }: PersonalityTestProps) {
  const { t } = useTranslation();
  const feedback = useFormFeedback(fieldErrors);
  const [answers, setAnswers] = useState<Record<string, number>>({});
  const answeredCount = questions.filter(question => answers[question.id] !== undefined).length;

  return (
    <Card padding="lg" className="sm-pt">
      <form noValidate onSubmit={event => { event.preventDefault(); void feedback.submit(() => onSubmit(answers)); }}>
        <header className="sm-pt__header">
          <span className="sm-eyebrow">{t('personality.title')}</span>
          <h2 className="sm-h2">{t('personality.heading')}</h2>
          <p className="sm-pt__lead">{t('personality.lead', { count: questions.length })}</p>
          <Banner tone="info">{t('personality.yearly')}</Banner>
        </header>
        <ol className="sm-pt__list">
          {questions.map((question, index) => {
            const error = feedback.fieldError(question.id);
            return (
              <li key={question.id}>
                <fieldset className={cx('sm-pt__item', !!error && 'sm-pt__item--error')} disabled={feedback.busy}>
                  <legend className="sm-pt__statement">
                    <span className="sm-num">{index + 1}</span>
                    {question.text}
                  </legend>
                  <div className="sm-pt__scale">
                    {LIKERT_VALUES.map(value => (
                      <label key={value} className="sm-pt__opt" data-v={value}>
                        <input type="radio" className="sm-visually-hidden" name={question.id} checked={answers[question.id] === value}
                          onChange={() => setAnswers(current => ({ ...current, [question.id]: value }))} />
                        <span className="sm-pt__dot" aria-hidden />
                        {t(`likert.${value}`)}
                      </label>
                    ))}
                  </div>
                  {error && <p className="sm-pt__error" role="alert">{error}</p>}
                </fieldset>
              </li>
            );
          })}
        </ol>
        <div className="sm-pt__bottom">
          <ProgressBar className="sm-pt__progress" label={t('personality.answered')} showLabel
            valueText={t('personality.progress', { answered: answeredCount, total: questions.length })}
            value={answeredCount} max={questions.length} />
          <Button type="submit" size="lg" loading={feedback.busy}>{t('personality.submit')}</Button>
        </div>
        {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      </form>
    </Card>
  );
}
