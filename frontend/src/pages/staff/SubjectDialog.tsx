import { Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getAcademicYears } from '../../api/academicYears';
import { getCategories } from '../../api/categories';
import { getDegrees } from '../../api/degrees';
import { isBackendError } from '../../api/errors';
import { registerEdition, registerSubject } from '../../api/subjects';
import { getTeachers } from '../../api/teachers';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Banner, Button, Chip, Dialog, IconButton, NumberStepper, Segmented, Select, TagInput, TextField, useFormFeedback } from '../../studymatch-ui';
import type { SubjectData, SubjectRequest } from '../../types/subjects';
import '../../styles/subjects.css';

interface DegreeRow { degreeId: string; year: number; semester: number }

interface SubjectDialogProps {
  open: boolean;
  onClose: () => void;
  // called with the subject once it's registered (or its edition, when the code already existed)
  onRegistered: (subject: SubjectData) => void;
}

const YEARS = [1, 2, 3, 4, 5, 6];

const formatYear = (startYear: number) => (startYear ? `${startYear}/${startYear + 1}` : '');

export function SubjectDialog({ open, onClose, onRegistered }: SubjectDialogProps) {
  const { t } = useTranslation();
  const degrees = useRemoteData('degrees', getDegrees);
  const categories = useRemoteData('categories', getCategories);
  const teachers = useRemoteData('teachers', getTeachers);
  const academicYears = useRemoteData('academic-years', getAcademicYears);
  const feedback = useFormFeedback([], {
    SUBJECT_ALREADY_IN_DEGREE: 'degrees',
    AT_LEAST_ONE_TEACHER_REQUIRED: 'teachers',
  });

  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [ects, setEcts] = useState(6);
  const [categoryId, setCategoryId] = useState('');
  const [degreeRows, setDegreeRows] = useState<DegreeRow[]>([{ degreeId: '', year: 1, semester: 1 }]);
  const [classes, setClasses] = useState<string[]>([]);
  const [teacherNumbers, setTeacherNumbers] = useState<number[]>([]);
  const [academicYear, setAcademicYear] = useState<number | null>(null);
  const [existingId, setExistingId] = useState<number | null>(null);

  // the newest year is the one the backend lists first
  const year = academicYear ?? academicYears.data?.[0]?.startYear ?? 0;
  const edition = () => ({ academicYear: year, teacherNumbers, classSections: classes });

  const addDegree = () => setDegreeRows((rows) => [...rows, { degreeId: '', year: 1, semester: 1 }]);
  const removeDegree = (index: number) => setDegreeRows((rows) => rows.filter((_, i) => i !== index));
  const setDegree = (index: number, patch: Partial<DegreeRow>) =>
    setDegreeRows((rows) => rows.map((row, i) => (i === index ? { ...row, ...patch } : row)));

  const addTeacher = (number: number) => {
    if (!number || teacherNumbers.includes(number)) return;
    setTeacherNumbers((list) => [...list, number]);
  };

  const buildRequest = (): SubjectRequest => ({
    code: code.trim(), name: name.trim(), ects, categoryId: Number(categoryId),
    degrees: degreeRows.map((row) => ({ degreeId: Number(row.degreeId), curricularYear: row.year, semester: row.semester })),
    edition: edition(),
  });

  const submitSubject = () => {
    void feedback.submit(async () => {
      try {
        const created = await registerSubject(buildRequest());
        onRegistered(created.subject);
      } catch (error) {
        // offer to register this year's edition of the subject that already has this code
        if (isBackendError(error) && error.code === 'SUBJECT_ALREADY_EXISTS') setExistingId(Number(error.params?.id));
        throw error;
      }
    });
  };

  const submitExistingEdition = () => {
    if (existingId == null) return;
    void feedback.submit(async () => {
      const created = await registerEdition(existingId, edition());
      onRegistered(created.subject);
    });
  };

  const availableDegrees = (degrees.data ?? []).filter(
    (degree) => !degreeRows.some((row) => Number(row.degreeId) === degree.id),
  );
  const availableTeachers = (teachers.data ?? []).filter((teacher) => !teacherNumbers.includes(teacher.number));

  return (
    <Dialog open={open} onClose={onClose} title={t('subjects.newSubject')} description={t('subjects.newSubjectDescription')} size="lg">
      <form className="sm-subject-form" noValidate onSubmit={(event) => { event.preventDefault(); submitSubject(); }}>
        <div className="sm-subject-form__top">
          <TextField containerClassName="sm-subject-form__name" label={t('fields.name')} value={name} disabled={feedback.busy}
            error={feedback.fieldError('name')} onChange={(event) => setName(event.target.value)} />
          <TextField label={t('subjects.code')} value={code} disabled={feedback.busy}
            error={feedback.fieldError('code')} onChange={(event) => setCode(event.target.value)} />
          <NumberStepper label={t('subjects.ects')} value={ects} min={1} max={60} disabled={feedback.busy}
            error={feedback.fieldError('ects')} onValueChange={setEcts} />
        </div>

        <Select label={t('subjects.category')} value={categoryId} disabled={feedback.busy}
          error={feedback.fieldError('categoryId')} onChange={(event) => setCategoryId(event.target.value)}
          options={(categories.data ?? []).map((category) => ({ value: String(category.id), label: category.name }))}
          placeholder={t('subjects.chooseCategory')} />

        <section className="sm-subject-degrees">
          <p className="sm-subject-degrees__title">{t('subjects.degrees')}</p>
          <p className="sm-subject-degrees__hint">{t('subjects.degreesHint')}</p>
          {feedback.fieldError('degrees') && <p role="alert" className="sm-subject-degrees__error">{feedback.fieldError('degrees')}</p>}
          {degreeRows.map((row, index) => (
            <div key={index} className="sm-subject-degrees__row">
              <Select label={t('subjects.degree')} value={row.degreeId} disabled={feedback.busy}
                onChange={(event) => setDegree(index, { degreeId: event.target.value })}
                options={availableDegrees.map((degree) => ({ value: String(degree.id), label: degree.name }))}
                placeholder={t('subjects.chooseDegree')} />
              <Select label={t('subjects.year')} value={String(row.year)} disabled={feedback.busy}
                onChange={(event) => setDegree(index, { year: Number(event.target.value) })}
                options={YEARS.map((value) => ({ value: String(value), label: t('subjects.yearOption', { year: value }) }))} />
              <Segmented label={t('subjects.semester')} size="sm" value={row.semester}
                options={[{ value: 1, label: t('subjects.firstSemester') }, { value: 2, label: t('subjects.secondSemester') }]}
                onValueChange={(value) => setDegree(index, { semester: value })} />
              <IconButton icon={Trash2} variant="ghost" size="sm" disabled={feedback.busy}
                label={t('subjects.removeDegree', { number: index + 1 })} onClick={() => removeDegree(index)} />
            </div>
          ))}
          <Button type="button" variant="tinted" size="sm" icon={Plus} disabled={feedback.busy}
            onClick={addDegree}>{t('subjects.addDegree')}</Button>
        </section>

        <div className="sm-subject-form__row">
          <Select label={t('subjects.academicYear')} value={String(year)} disabled={feedback.busy || year === 0}
            onChange={(event) => setAcademicYear(Number(event.target.value))}
            options={(academicYears.data ?? []).map((item) => ({ value: String(item.startYear), label: formatYear(item.startYear) }))} />
          <TagInput label={t('subjects.classes')} value={classes} onValueChange={setClasses}
            placeholder={t('subjects.classesPlaceholder')} />
        </div>

        <section className="sm-subject-teachers">
          <p className="sm-subject-teachers__label">{t('subjects.teachersHint')}</p>
          {feedback.fieldError('teachers') && <p role="alert" className="sm-subject-teachers__error">{feedback.fieldError('teachers')}</p>}
          <div className="sm-chip-group">
            {teacherNumbers.map((number) => {
              const teacher = teachers.data?.find((item) => item.number === number);
              return (
                <Chip key={number} onRemove={() => setTeacherNumbers((list) => list.filter((n) => n !== number))}
                  removeLabel={t('subjects.removeTeacher', { name: teacher?.name ?? number })} disabled={feedback.busy}>
                  {teacher?.name ?? number}
                </Chip>
              );
            })}
          </div>
          <Select label={t('subjects.addTeacher')} value="" disabled={feedback.busy}
            onChange={(event) => addTeacher(Number(event.target.value))}
            options={availableTeachers.map((item) => ({ value: String(item.number), label: item.name }))}
            placeholder={t('subjects.chooseTeacher')} />
        </section>

        {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}

        {existingId != null && (
          <Banner tone="info" title={t('subjects.codeExistsTitle', { code })}
            action={<Button variant="primary" size="sm" disabled={feedback.busy} onClick={submitExistingEdition}>{t('subjects.createEdition', { year: formatYear(year) })}</Button>}>
            {t('subjects.codeExistsBody', { name })}
          </Banner>
        )}

        <div className="sm-subject-form__actions">
          <Button variant="secondary" type="button" onClick={onClose} disabled={feedback.busy}>{t('common.cancel')}</Button>
          <Button type="submit" icon={Plus} loading={feedback.busy}>{t('subjects.registerSubject')}</Button>
        </div>
      </form>
    </Dialog>
  );
}
