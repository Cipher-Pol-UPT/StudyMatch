import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { getAnalytics } from '../../api/analytics';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Banner, BarChart, Card, DonutChart, PROFILE_KEYS, PROFILES, Select, Skeleton, StatCard } from '../../studymatch-ui';
import type { AnalyticsData, AnalyticsFiltersData } from '../../types/analytics';
import './analytics.css';

// choosing a broader filter clears the narrower ones under it
const NARROWER: Record<string, string[]> = {
  academicYear: ['subjectEditionId', 'classSection', 'groupId'],
  subjectEditionId: ['classSection', 'groupId'],
  classSection: [],
  groupId: [],
};

export function StaffAnalyticsPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const query = params.toString();
  // the options stay while a new selection loads
  const [options, setOptions] = useState<AnalyticsFiltersData>();
  const load = useCallback(async () => {
    const data = await getAnalytics(query);
    setOptions(data.filters);
    return data;
  }, [query]);
  const analytics = useRemoteData(`analytics?${query}`, load);

  const choose = (name: string, value: string) => {
    const next = new URLSearchParams(params);
    for (const narrower of NARROWER[name]) next.delete(narrower);
    if (value) next.set(name, value);
    else next.delete(name);
    setParams(next, { replace: true });
  };

  const editionId = params.get('subjectEditionId') ?? '';
  const edition = options?.subjectEditions.find(item => String(item.id) === editionId);
  const groups = options?.groups.filter(group => !edition || group.subjectEditionId === edition.id) ?? [];
  const year = params.get('academicYear') ?? (analytics.data?.academicYear ? String(analytics.data.academicYear) : '');

  return (
    <div className="role-page analytics">
      <header>
        <h1 className="sm-h2">{t('workspace.sections.analytics')}</h1>
        <p className="sm-caption">{t('analytics.intro')}</p>
      </header>
      <form className="analytics__filters" aria-label={t('analytics.filters')} onSubmit={event => event.preventDefault()}>
        <Select label={t('analytics.year')} value={year} disabled={!options}
          options={(options?.academicYears ?? []).map(value => ({ value: String(value), label: `${value}/${value + 1}` }))}
          onChange={event => choose('academicYear', event.target.value)} />
        <Select label={t('directory.subject')} value={editionId} disabled={!options}
          options={[{ value: '', label: t('analytics.allSubjects') },
            ...(options?.subjectEditions ?? []).map(item => ({ value: String(item.id), label: `${item.subjectCode} · ${item.subjectName}` }))]}
          onChange={event => choose('subjectEditionId', event.target.value)} />
        <Select label={t('directory.class')} value={params.get('classSection') ?? ''} disabled={!edition}
          options={[{ value: '', label: t('analytics.allClasses') }, ...(edition?.classSections ?? []).map(code => ({ value: code, label: code }))]}
          onChange={event => choose('classSection', event.target.value)} />
        <Select label={t('analytics.group')} value={params.get('groupId') ?? ''} disabled={groups.length === 0}
          options={[{ value: '', label: t('analytics.allGroups') },
            ...groups.map(group => ({ value: String(group.id), label: t('analytics.groupOf', { assignment: group.assignment, number: group.number }) }))]}
          onChange={event => choose('groupId', event.target.value)} />
      </form>
      {analytics.error !== undefined ? (
        <DataLoadState error={analytics.error} retry={analytics.retry} />
      ) : analytics.data?.totalStudents === 0 ? (
        <Banner tone="info" title={t('analytics.noData')}>{t('analytics.noDataHint')}</Banner>
      ) : (
        <AnalyticsCards data={analytics.data} />
      )}
    </div>
  );
}

// undefined while loading: every card keeps its place with a skeleton
function AnalyticsCards({ data }: { data?: AnalyticsData }) {
  const { t, i18n } = useTranslation();
  const loading = <Skeleton h={180} r={12} />;
  const percent = (value: number) => `${Math.round(value)}%`;
  const month = (value: string) => new Date(`${value}-01T00:00`).toLocaleDateString(i18n.language, { month: 'short', year: '2-digit' });
  const traits = PROFILE_KEYS.map(key => ({
    label: PROFILES[key].label, color: PROFILES[key].color,
    value: data?.personalityDistribution.find(item => item.trait === key.toUpperCase())?.students ?? 0,
  }));
  const withProfile = traits.reduce((sum, trait) => sum + trait.value, 0);

  return (
    <>
      <div className="role-grid">
        <StatCard label={t('analytics.students')} value={data ? data.totalStudents : <Skeleton w={64} h={32} />} />
        <StatCard label={t('analytics.activeSubjects')} value={data ? data.activeSubjects : <Skeleton w={64} h={32} />} />
        <StatCard label={t('analytics.averageAttendance')}
          value={!data ? <Skeleton w={64} h={32} /> : data.averageAttendance === null ? '—' : Math.round(data.averageAttendance)}
          unit={data?.averageAttendance != null ? '%' : undefined} />
      </div>
      <div className="analytics__charts">
        <Card title={t('analytics.attendanceOverTime')}>
          {!data ? loading : data.attendanceOverTime.length === 0 ? <p className="sm-caption">{t('analytics.noLessons')}</p> : (
            <BarChart label={t('analytics.attendanceByMonth')} max={100} format={percent}
              data={data.attendanceOverTime.map(item => ({ label: month(item.month), value: item.attendance }))} />
          )}
        </Card>
        <Card title={t('analytics.strongestTrait')}>
          {!data ? loading : withProfile === 0 ? <p className="sm-caption">{t('analytics.noProfiles')}</p> : (
            <DonutChart label={t('analytics.studentsByTrait')} size={150} segments={traits}
              center={withProfile} centerCaption={t('analytics.studentsCaption', { count: withProfile })} />
          )}
        </Card>
      </div>
      <Card title={t('analytics.groupBalance')} description={t('analytics.groupBalanceDescription')}>
        {!data ? loading : <GroupBalance data={data} />}
      </Card>
    </>
  );
}

// how far each group's average grade is from the average of its assignment's groups
function GroupBalance({ data }: { data: AnalyticsData }) {
  const { t } = useTranslation();
  if (data.firstYearOnly) return <p className="sm-caption">{t('analytics.noGradeHistory')}</p>;
  const graded = data.groupBalance.filter(group => group.averageGrade !== null);
  if (graded.length === 0) return <p className="sm-caption">{t('analytics.noGroups')}</p>;
  const means = new Map<string, number>();
  for (const assignment of new Set(graded.map(group => group.assignment))) {
    const grades = graded.filter(group => group.assignment === assignment).map(group => group.averageGrade ?? 0);
    means.set(assignment, grades.reduce((sum, grade) => sum + grade, 0) / grades.length);
  }
  return (
    <BarChart label={t('analytics.groupBalance')} format={value => `±${value.toFixed(1)}`}
      data={graded.map(group => ({
        label: means.size > 1 ? `${group.assignment} G${group.number}` : `G${group.number}`,
        value: Math.abs((group.averageGrade ?? 0) - (means.get(group.assignment) ?? 0)),
      }))} />
  );
}
