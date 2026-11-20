import { BookOpen, CalendarClock } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getAttendanceSheet } from '../../api/attendance';
import { getCurrentEditions } from '../../api/enrolments';
import { isBackendError } from '../../api/errors';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { EmptyState, Select, TextField } from '../../studymatch-ui';
import { AttendanceSheet } from './AttendanceSheet';
import './attendance.css';

const dateInput = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

export function StaffAttendancePage() {
  const { t } = useTranslation();
  const editions = useRemoteData('current-editions', getCurrentEditions);
  const [editionId, setEditionId] = useState('');
  const [classSection, setClassSection] = useState('');
  const [date, setDate] = useState(() => dateInput(new Date()));
  const [time, setTime] = useState('');

  // the first edition and its first class are picked until Staff chooses
  const edition = editions.data?.find(item => String(item.id) === editionId) ?? editions.data?.[0];
  const section = edition?.classSections.includes(classSection) ? classSection : edition?.classSections[0] ?? '';
  const startsAt = date && time ? `${date}T${time}:00` : '';
  const lessonId = edition && section && startsAt ? edition.id : undefined;

  // nothing to load until the whole lesson is chosen
  const load = useCallback(() => lessonId ? getAttendanceSheet(lessonId, section, startsAt) : Promise.resolve(null), [lessonId, section, startsAt]);
  const sheet = useRemoteData(`${lessonId}|${section}|${startsAt}`, load);
  const future = isBackendError(sheet.error) && sheet.error.code === 'LESSON_IN_FUTURE';

  return (
    <div className="role-page directory-page">
      <header>
        <h1 className="sm-h2">{t('workspace.sections.attendance')}</h1>
        <p className="sm-caption">{t('attendance.intro')}</p>
      </header>
      {editions.loading || editions.error !== undefined ? (
        <DataLoadState error={editions.error} retry={editions.retry} />
      ) : !edition ? (
        <EmptyState icon={BookOpen} title={t('enrolments.noEditions')} body={t('attendance.noEditionsBody')} />
      ) : (
        <div className="attendance">
          <div className="attendance__lesson">
            <Select label={t('directory.subject')} value={String(edition.id)} onChange={event => setEditionId(event.target.value)}
              options={(editions.data ?? []).map(item => ({ value: String(item.id), label: `${item.subject.code} · ${item.subject.name}` }))} />
            <Select label={t('directory.class')} value={section} onChange={event => setClassSection(event.target.value)}
              options={edition.classSections.map(code => ({ value: code, label: code }))} />
            <TextField label={t('rooms.date')} type="date" value={date} onChange={event => setDate(event.target.value)}
              error={future ? t('errors.LESSON_IN_FUTURE') : undefined} />
            <TextField label={t('rooms.start')} type="time" value={time} onChange={event => setTime(event.target.value)} />
          </div>
          {!lessonId ? (
            <EmptyState icon={CalendarClock} title={t('attendance.chooseLesson')} body={t('attendance.chooseLessonBody')} />
          ) : future ? null : sheet.loading || sheet.error !== undefined || !sheet.data ? (
            <DataLoadState error={sheet.error} retry={sheet.retry} />
          ) : (
            <AttendanceSheet key={`${lessonId}|${section}|${startsAt}`} editionId={lessonId} subject={edition.subject.name} sheet={sheet.data} />
          )}
        </div>
      )}
    </div>
  );
}
