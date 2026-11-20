import { CalendarClock } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getAttendanceSheet } from '../../api/attendance';
import { isBackendError } from '../../api/errors';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { EmptyState, Select, TextField } from '../../studymatch-ui';
import { AttendanceSheet } from './AttendanceSheet';
import { CurrentEditionPage } from './CurrentEditionPage';
import { useCurrentEdition } from './useCurrentEdition';
import './attendance.css';

const dateInput = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

export function StaffAttendancePage() {
  const { t } = useTranslation();
  const current = useCurrentEdition();
  const { editions, edition, section } = current;
  const [date, setDate] = useState(() => dateInput(new Date()));
  const [time, setTime] = useState('');
  const startsAt = date && time ? `${date}T${time}:00` : '';
  const lessonId = edition && section && startsAt ? edition.id : undefined;

  // nothing to load until the whole lesson is chosen
  const load = useCallback(() => lessonId ? getAttendanceSheet(lessonId, section, startsAt) : Promise.resolve(null), [lessonId, section, startsAt]);
  const sheet = useRemoteData(`${lessonId}|${section}|${startsAt}`, load);
  const future = isBackendError(sheet.error) && sheet.error.code === 'LESSON_IN_FUTURE';

  return (
    <CurrentEditionPage title={t('workspace.sections.attendance')} intro={t('attendance.intro')} emptyBody={t('attendance.noEditionsBody')} current={current}>
      {chosen => (
        <div className="attendance">
          <div className="attendance__lesson">
            <Select label={t('directory.subject')} value={String(chosen.id)} onChange={event => current.setEditionId(event.target.value)}
              options={(editions.data ?? []).map(item => ({ value: String(item.id), label: `${item.subject.code} · ${item.subject.name}` }))} />
            <Select label={t('directory.class')} value={section} onChange={event => current.setClassSection(event.target.value)}
              options={chosen.classSections.map(code => ({ value: code, label: code }))} />
            <TextField label={t('rooms.date')} type="date" value={date} onChange={event => setDate(event.target.value)}
              error={future ? t('errors.LESSON_IN_FUTURE') : undefined} />
            <TextField label={t('rooms.start')} type="time" value={time} onChange={event => setTime(event.target.value)} />
          </div>
          {!lessonId ? (
            <EmptyState icon={CalendarClock} title={t('attendance.chooseLesson')} body={t('attendance.chooseLessonBody')} />
          ) : future ? null : sheet.loading || sheet.error !== undefined || !sheet.data ? (
            <DataLoadState error={sheet.error} retry={sheet.retry} />
          ) : (
            <AttendanceSheet key={`${lessonId}|${section}|${startsAt}`} editionId={lessonId} subject={chosen.subject.name} sheet={sheet.data} />
          )}
        </div>
      )}
    </CurrentEditionPage>
  );
}
