import { ClipboardPlus, Plus } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { isBackendError, type ErrorCode } from '../../api/errors';
import { Banner, Button, Chip, ControlRow, NumberStepper, Segmented, Select, Switch, TextField, useFormFeedback } from '../../studymatch-ui';
import type { AssignmentRequest, SeparationData } from '../../types/assignments';
import { AssignmentType } from '../../types/enums';
import './assignments.css';

const dateInput = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

const GROUP_SIZE = { min: 2, max: 20 } as const;

// the business errors the issue wants under a field, the others go to the banner
const FIELD_OF: Partial<Record<ErrorCode, string>> = {
  ASSIGNMENT_ALREADY_EXISTS: 'name',
  ASSIGNMENT_WEIGHT_EXCEEDED: 'weight',
  GROUP_WORK_NOT_ALLOWED_FOR_EXAMS: 'groupWork',
  INVALID_GROUP_SIZES: 'groups',
  SEPARATION_SAME_STUDENT: 'separations',
  SEPARATION_ALREADY_EXISTS: 'separations',
  STUDENT_NOT_ENROLLED: 'separations',
};

export interface AssignmentFormProps {
  remaining: number;
  students: { number: number; name: string }[];
  onSubmit: (assignment: AssignmentRequest) => Promise<void>;
  onCancel: () => void;
  today?: Date;
}

// group work only for projects; it opens the sizes, same class and pairs to keep apart
export function AssignmentForm({ remaining, students, onSubmit, onCancel, today = new Date() }: AssignmentFormProps) {
  const { t } = useTranslation();
  const feedback = useFormFeedback();
  const [name, setName] = useState('');
  const [type, setType] = useState<AssignmentType>('PROJECT');
  const [date, setDate] = useState('');
  const [weight, setWeight] = useState(Math.min(Math.max(remaining, 1), 30));
  const [groupWork, setGroupWork] = useState(false);
  const [sizes, setSizes] = useState({ minSize: 3, optimalSize: 4, maxSize: 5 });
  const [sameClass, setSameClass] = useState(false);
  const [separations, setSeparations] = useState<SeparationData[]>([]);
  const [pair, setPair] = useState<{ first: string; second: string } | null>(null);
  const [codeError, setCodeError] = useState<{ field: string; message: string } | null>(null);
  const grouped = type === 'PROJECT' && groupWork;
  const overWeight = weight > remaining;
  const nameOf = (number: number) => students.find(student => student.number === number)?.name ?? String(number);

  const errorFor = (field: string, ...backendFields: string[]) => codeError?.field === field
    ? codeError.message
    : backendFields.map(feedback.fieldError).filter(Boolean).join(' ') || undefined;

  function addPair() {
    if (!pair) return;
    const [first, second] = [Number(pair.first), Number(pair.second)];
    // the same two students in either order
    const exists = separations.some(item => (item.firstStudent === first && item.secondStudent === second)
      || (item.firstStudent === second && item.secondStudent === first));
    if (!exists) setSeparations(list => [...list, { firstStudent: first, secondStudent: second }]);
    setPair(null);
  }

  async function save() {
    setCodeError(null);
    if (overWeight) return;
    try {
      await onSubmit({
        name: name.trim(), type, date: date || null, weight,
        grouping: grouped ? { ...sizes, sameClass, separations } : null,
      });
    } catch (error) {
      const field = isBackendError(error) ? FIELD_OF[error.code] : undefined;
      if (!field || !isBackendError(error)) throw error;
      setCodeError({ field, message: t(`errors.${error.code}`, error.params ?? {}) });
    }
  }

  return (
    <form className="assignment-form" noValidate onSubmit={event => { event.preventDefault(); void feedback.submit(save); }}>
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      <TextField label={t('assignments.name')} value={name} disabled={feedback.busy} error={errorFor('name', 'name')}
        onChange={event => setName(event.target.value)} />
      <Segmented<AssignmentType> label={t('assignments.type')} value={type} block onValueChange={setType}
        options={AssignmentType.map(value => ({ value, label: t(`assignments.types.${value}`) }))} />
      <div className="assignment-form__row">
        <TextField label={t('assignments.date')} type="date" value={date} min={dateInput(today)} disabled={feedback.busy}
          error={errorFor('date', 'date')} onChange={event => setDate(event.target.value)} />
        <div className="assignment-form__box">
          <NumberStepper label={t('assignments.weight')} value={weight} min={1} max={100} onValueChange={setWeight} disabled={feedback.busy}
            hint={t('assignments.weightLeft', { remaining })}
            error={overWeight ? t('assignments.weightOver', { remaining }) : errorFor('weight', 'weight')} />
        </div>
      </div>
      <div>
        <ControlRow title={t('assignments.groupWork')} description={t(type === 'PROJECT' ? 'assignments.groupWorkHint' : 'assignments.alwaysIndividual')}>
          <Switch aria-label={t('assignments.groupWork')} checked={grouped} disabled={type !== 'PROJECT' || feedback.busy} onCheckedChange={setGroupWork} />
        </ControlRow>
        {errorFor('groupWork') && <p className="assignment-form__error" role="alert">{errorFor('groupWork')}</p>}
      </div>
      {grouped && (
        <fieldset className="assignment-form__groups">
          <legend className="assignment-form__title">{t('assignments.groups')}</legend>
          <p className="assignment-form__note">{t('assignments.groupsRule')}</p>
          {(['minSize', 'optimalSize', 'maxSize'] as const).map(key => (
            <NumberStepper key={key} label={t(`assignments.${key}`)} value={sizes[key]} min={GROUP_SIZE.min} max={GROUP_SIZE.max} disabled={feedback.busy}
              onValueChange={value => setSizes(current => ({ ...current, [key]: value }))} />
          ))}
          {errorFor('groups', 'grouping.minSize', 'grouping.optimalSize', 'grouping.maxSize') && (
            <p className="assignment-form__error" role="alert">{errorFor('groups', 'grouping.minSize', 'grouping.optimalSize', 'grouping.maxSize')}</p>
          )}
          <ControlRow title={t('assignments.sameClass')}>
            <Switch aria-label={t('assignments.sameClass')} checked={sameClass} disabled={feedback.busy} onCheckedChange={setSameClass} />
          </ControlRow>
          <div className="assignment-form__apart">
            <span className="assignment-form__label">{t('assignments.keepApart')}</span>
            {separations.length > 0 && (
              <div className="assignment-form__chips">
                {separations.map(item => (
                  <Chip key={`${item.firstStudent}-${item.secondStudent}`} removeLabel={t('assignments.removePair')} disabled={feedback.busy}
                    onRemove={() => setSeparations(list => list.filter(other => other !== item))}>
                    {nameOf(item.firstStudent)} ↔ {nameOf(item.secondStudent)}
                  </Chip>
                ))}
              </div>
            )}
            {pair ? (
              <div className="assignment-form__pair">
                {(['first', 'second'] as const).map(side => (
                  <Select key={side} label={t(`assignments.${side}Student`)} value={pair[side]} placeholder={t('assignments.chooseStudent')}
                    options={students.map(student => ({ value: String(student.number), label: `${student.name} · ${student.number}` }))}
                    onChange={event => setPair({ ...pair, [side]: event.target.value })} />
                ))}
                <div className="assignment-form__actions">
                  <Button variant="ghost" size="sm" onClick={() => setPair(null)}>{t('common.cancel')}</Button>
                  <Button variant="tinted" size="sm" disabled={!pair.first || !pair.second || pair.first === pair.second} onClick={addPair}>
                    {t('assignments.addPair')}
                  </Button>
                </div>
              </div>
            ) : (
              <Button variant="tinted" size="sm" icon={Plus} disabled={feedback.busy || students.length < 2} onClick={() => setPair({ first: '', second: '' })}>
                {t('assignments.addPair')}
              </Button>
            )}
            {errorFor('separations', 'grouping.separations') && (
              <p className="assignment-form__error" role="alert">{errorFor('separations', 'grouping.separations')}</p>
            )}
          </div>
        </fieldset>
      )}
      <div className="assignment-form__actions">
        <Button variant="secondary" onClick={onCancel} disabled={feedback.busy}>{t('ui.close')}</Button>
        <Button type="submit" icon={ClipboardPlus} loading={feedback.busy}>{t('assignments.create')}</Button>
      </div>
    </form>
  );
}
