import { GraduationCap, Send } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { isBackendError } from '../../api/errors';
import { applyForTutoring, getApplicableSubjects } from '../../api/studyRooms';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Banner, Button, Dialog, EmptyState, StatusPill, Table, Textarea, useFormFeedback, useToast, type StatusTone, type TableColumn } from '../../studymatch-ui';
import type { TutorEligibleSubjectData } from '../../types/studyRooms';

const MOTIVATION_MAX = 500;

type TutorState = 'tutor' | 'pending' | 'notTaught' | 'notTop' | 'canApply';
const TONES: Record<TutorState, StatusTone> = { tutor: 'success', pending: 'warning', notTaught: 'neutral', notTop: 'neutral', canApply: 'violet' };

// a rejected application leaves nothing pending, so the subject can be applied for again while eligible
function stateOf(subject: TutorEligibleSubjectData): TutorState {
  if (subject.hasTutorRole) return 'tutor';
  if (subject.hasPendingApplication) return 'pending';
  if (!subject.hasCurrentEdition) return 'notTaught';
  return subject.eligible ? 'canApply' : 'notTop';
}

// what the backend refused with says what the row really is now
const REFUSALS: Partial<Record<string, Partial<TutorEligibleSubjectData>>> = {
  TUTOR_APPLICATION_ALREADY_PENDING: { hasPendingApplication: true },
  TUTOR_ROLE_ALREADY_EXISTS: { hasTutorRole: true },
  NOT_ELIGIBLE_FOR_TUTORING: { eligible: false },
  SUBJECT_NOT_TAUGHT_THIS_YEAR: { hasCurrentEdition: false },
};

// Become a tutor (#40): every subject you passed, your rank in it, and Apply where you're in the top 5%
export function BecomeTutor() {
  const { t } = useTranslation();
  const toast = useToast();
  const subjects = useRemoteData('tutors/me/applicable-subjects', getApplicableSubjects);
  // rows changed here, on top of what was loaded
  const [changed, setChanged] = useState<ReadonlyMap<number, Partial<TutorEligibleSubjectData>>>(() => new Map());
  const [applying, setApplying] = useState<TutorEligibleSubjectData | null>(null);

  const change = (subjectId: number, fields: Partial<TutorEligibleSubjectData>) =>
    setChanged(current => new Map(current).set(subjectId, { ...current.get(subjectId), ...fields }));
  const rows = subjects.data?.map(subject => ({ ...subject, ...changed.get(subject.subjectId) }));

  const columns: TableColumn<TutorEligibleSubjectData>[] = [
    { key: 'subject', header: t('tutoring.subject'), render: row => row.subjectName },
    { key: 'grade', header: t('tutoring.grade'), align: 'right', render: row => row.finalGrade },
    { key: 'position', header: t('tutoring.position'), render: row => t('tutoring.positionOf', { position: row.position, size: row.editionSize }) },
    {
      key: 'state', header: t('tutoring.state'), render: row => {
        const state = stateOf(row);
        return <StatusPill size="sm" tone={TONES[state]}>{t(`tutoring.states.${state}`)}</StatusPill>;
      },
    },
    {
      key: 'apply', header: <span className="sm-visually-hidden">{t('tutoring.actions')}</span>, align: 'right', width: 120,
      render: row => stateOf(row) === 'canApply' && (
        <Button size="sm" variant="tinted" aria-label={t('tutoring.applyFor', { subject: row.subjectName })} onClick={() => setApplying(row)}>
          {t('tutoring.apply')}
        </Button>
      ),
    },
  ];

  return (
    <section aria-labelledby="study-rooms-list-title" className="study-rooms__personal study-rooms__tutor">
      <header className="study-rooms__list-heading">
        <div>
          <h2 id="study-rooms-list-title">{t('tutoring.heading')}</h2>
          <p>{t('tutoring.hint')}</p>
        </div>
      </header>
      {subjects.loading || subjects.error !== undefined || !rows ? (
        <DataLoadState error={subjects.error} retry={subjects.retry} />
      ) : (
        <Table columns={columns} rows={rows} rowKey={row => row.subjectId}
          empty={<EmptyState icon={GraduationCap} title={t('tutoring.empty')} body={t('tutoring.emptyBody')} />} />
      )}

      <Dialog open={applying !== null} onClose={() => setApplying(null)} title={t('tutoring.applyTitle', { subject: applying?.subjectName })}
        description={t('tutoring.applyDescription')}>
        {applying && (
          <ApplyForm key={applying.subjectId} subject={applying} onCancel={() => setApplying(null)}
            onApplied={() => {
              change(applying.subjectId, { hasPendingApplication: true });
              toast.success(t('tutoring.applied', { subject: applying.subjectName }));
              setApplying(null);
            }}
            onRefused={(code) => {
              change(applying.subjectId, REFUSALS[code] ?? {});
              toast.danger(t(`errors.${code}`));
              setApplying(null);
            }} />
        )}
      </Dialog>
    </section>
  );
}

interface ApplyFormProps {
  subject: TutorEligibleSubjectData;
  onCancel: () => void;
  onApplied: () => void;
  // the subject can't be applied for any more: a toast, and the row shows why
  onRefused: (code: string) => void;
}

function ApplyForm({ subject, onCancel, onApplied, onRefused }: ApplyFormProps) {
  const { t } = useTranslation();
  const [motivation, setMotivation] = useState('');
  const feedback = useFormFeedback();

  const apply = (event: FormEvent) => {
    event.preventDefault();
    void feedback.submit(async () => {
      try {
        await applyForTutoring(subject.subjectId, motivation.trim() || null);
        onApplied();
      } catch (error) {
        if (isBackendError(error) && REFUSALS[error.code]) return onRefused(error.code);
        throw error;
      }
    });
  };

  return (
    <form className="study-rooms__apply" onSubmit={apply} noValidate>
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      <p className="sm-caption">{t('tutoring.applyRank', { grade: subject.finalGrade, position: subject.position, size: subject.editionSize })}</p>
      <Textarea label={t('tutoring.motivation')} optional placeholder={t('tutoring.motivationPlaceholder')} value={motivation}
        onChange={event => setMotivation(event.target.value)} maxLength={MOTIVATION_MAX} showCount disabled={feedback.busy}
        error={feedback.fieldError('motivation')} />
      <div className="sm-dialog__actions">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>{t('common.cancel')}</Button>
        <Button type="submit" size="sm" icon={Send} loading={feedback.busy}>{t('tutoring.apply')}</Button>
      </div>
    </form>
  );
}
