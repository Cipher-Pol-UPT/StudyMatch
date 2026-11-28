import type { TFunction } from 'i18next';
import { CalendarOff, CalendarX, Plus, Trash2 } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { addDayOff, getAcademicYears, getDaysOff, removeDayOff } from '../../api/academicYears';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Banner, Button, Calendar, Card, Dialog, EmptyState, Table, useToast, type TableColumn } from '../../studymatch-ui';
import type { AcademicYearData, DayOffData, DayOffRequest, SemesterDatesData } from '../../types/academicYears';
import { DayOffForm } from './DayOffForm';
import './daysOff.css';

// yyyy-mm-dd as a local date, so a day doesn't move with the time zone
const localDate = (day: string) => new Date(`${day}T00:00:00`);
const isoDay = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const byFirstDay = (a: DayOffData, b: DayOffData) => a.firstDay.localeCompare(b.firstDay);

// every day of each range, for the dots on the calendar
function datesOf(days: DayOffData[]) {
  return days.flatMap(day => {
    const dates: string[] = [];
    for (let date = localDate(day.firstDay); isoDay(date) <= day.lastDay; date.setDate(date.getDate() + 1)) dates.push(isoDay(date));
    return dates;
  });
}

// Staff → Academic year: the current year with its semesters, and its days without classes (#337). Starting the
// next year (#67) and editing the semester dates (#335) go on this page too
export function StaffAcademicYearPage() {
  const { t } = useTranslation();
  const years = useRemoteData('academic-years', getAcademicYears);
  const current = years.data?.[0];

  return (
    <div className="role-page">
      <header>
        <h1 className="sm-h2">{t('workspace.sections.academicYear')}</h1>
        {current && <p className="sm-caption">{t('daysOff.year', { from: current.startYear, to: current.startYear + 1 })}</p>}
      </header>
      {years.loading || years.error !== undefined ? (
        <DataLoadState error={years.error} retry={years.retry} />
      ) : current ? (
        <DaysOff year={current} />
      ) : (
        <EmptyState icon={CalendarX} title={t('daysOff.noYear')} body={t('daysOff.noYearBody')} />
      )}
    </div>
  );
}

function DaysOff({ year }: { year: AcademicYearData }) {
  const { t, i18n } = useTranslation();
  const toast = useToast();
  const load = useCallback(() => getDaysOff(year.startYear), [year.startYear]);
  const loaded = useRemoteData(`days-off-${year.startYear}`, load);
  // added and removed here, on top of what was loaded
  const [added, setAdded] = useState<DayOffData[]>([]);
  const [removed, setRemoved] = useState<number[]>([]);
  const [removing, setRemoving] = useState<number>();
  // the form, with the day picked on the calendar if there was one
  const [adding, setAdding] = useState<{ firstDay?: string }>();
  const semesters = year.semesters;
  const days = [...(loaded.data ?? []), ...added].filter(day => !removed.includes(day.id)).sort(byFirstDay);

  const dayFormat = new Intl.DateTimeFormat(i18n.language, { day: 'numeric', month: 'long', year: 'numeric' });
  const formatDay = (day: string) => dayFormat.format(localDate(day));
  const formatDays = (day: DayOffData) =>
    day.firstDay === day.lastDay ? formatDay(day.firstDay) : dayFormat.formatRange(localDate(day.firstDay), localDate(day.lastDay));

  const retry = () => {
    setAdded([]);
    setRemoved([]);
    loaded.retry();
  };

  async function add(request: DayOffRequest) {
    const day = await addDayOff(year.startYear, request);
    setAdded(list => [...list, day]);
    setAdding(undefined);
    toast.success(t('daysOff.added', { days: formatDays(day) }));
  }

  async function remove(day: DayOffData) {
    setRemoving(day.id);
    try {
      await removeDayOff(year.startYear, day.id);
      setRemoved(list => [...list, day.id]);
      toast.success(t('daysOff.removed', { days: formatDays(day) }));
    } catch {
      toast.danger(t('common.unexpectedError'));
    } finally {
      setRemoving(undefined);
    }
  }

  const columns: TableColumn<DayOffData>[] = [
    { key: 'days', header: t('daysOff.days'), render: row => <span className="days-off__days">{formatDays(row)}</span> },
    { key: 'reason', header: t('daysOff.reason') },
    {
      key: 'actions', header: <span className="sm-visually-hidden">{t('ui.actions')}</span>, align: 'right',
      render: row => (
        <Button size="sm" variant="ghost" icon={Trash2} loading={removing === row.id} aria-label={t('daysOff.removeOf', { days: formatDays(row) })}
          onClick={() => void remove(row)}>
          {t('daysOff.remove')}
        </Button>
      ),
    },
  ];

  return (
    <Card className="days-off">
      <div className="days-off__head">
        <div>
          <h2 className="sm-h4">{t('daysOff.title')}</h2>
          {semesters && <p className="sm-caption">{semesterLine(semesters, dayFormat, t)}</p>}
        </div>
        <Button icon={Plus} disabled={!semesters || loaded.loading} onClick={() => setAdding({})}>{t('daysOff.add')}</Button>
      </div>
      {!semesters ? (
        <Banner tone="info">{t('daysOff.noSemesters')}</Banner>
      ) : loaded.loading || loaded.error !== undefined ? (
        <DataLoadState error={loaded.error} retry={retry} />
      ) : (
        <div className="days-off__body">
          <Calendar className="days-off__calendar" min={localDate(semesters.semester1Start)} max={localDate(semesters.semester2End)}
            defaultMonth={monthToShow(semesters)} events={datesOf(days).map(date => ({ date, label: t('daysOff.title') }))}
            onValueChange={date => setAdding({ firstDay: isoDay(date) })} />
          <Table label={t('daysOff.title')} columns={columns} rows={days} rowKey={row => row.id}
            empty={<EmptyState icon={CalendarOff} compact title={t('daysOff.none')} body={t('daysOff.noneBody')} />} />
        </div>
      )}
      <Dialog open={adding !== undefined} onClose={() => setAdding(undefined)} title={t('daysOff.add')} description={t('daysOff.addHint')}>
        {adding && semesters && (
          <DayOffForm key={adding.firstDay ?? 'new'} from={semesters.semester1Start} to={semesters.semester2End} firstDay={adding.firstDay}
            formatDay={formatDay} onSubmit={add} onCancel={() => setAdding(undefined)} />
        )}
      </Dialog>
    </Card>
  );
}

// "Semester 1: 15 September 2026 – 31 January 2027 · Semester 2: ..."
function semesterLine(semesters: SemesterDatesData, format: Intl.DateTimeFormat, t: TFunction) {
  const range = (from: string, to: string) => format.formatRange(localDate(from), localDate(to));
  return [
    t('daysOff.semester', { number: 1, dates: range(semesters.semester1Start, semesters.semester1End) }),
    t('daysOff.semester', { number: 2, dates: range(semesters.semester2Start, semesters.semester2End) }),
  ].join(' · ');
}

// today's month while classes run, otherwise the first month of classes
function monthToShow(semesters: SemesterDatesData) {
  const today = isoDay(new Date());
  return today >= semesters.semester1Start && today <= semesters.semester2End ? undefined : localDate(semesters.semester1Start);
}
