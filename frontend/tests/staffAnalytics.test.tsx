import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { StaffArea } from '../src/pages/staff/StaffArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { AnalyticsData } from '../src/types/analytics';

const analytics: AnalyticsData = {
  academicYear: 2026, totalStudents: 12, activeSubjects: 3, averageAttendance: 87.4,
  attendanceOverTime: [{ month: '2026-10', attendance: 90 }, { month: '2026-11', attendance: 84.8 }],
  personalityDistribution: [
    { trait: 'OPENNESS', students: 4 }, { trait: 'CONSCIENTIOUSNESS', students: 3 }, { trait: 'EXTRAVERSION', students: 2 },
    { trait: 'AGREEABLENESS', students: 1 }, { trait: 'NEUROTICISM', students: 0 },
  ],
  groupBalance: [
    { groupId: 7, assignmentId: 5, assignment: 'Test Project', subjectCode: 'TST1', number: 1, members: 3, averageGrade: 14 },
    { groupId: 8, assignmentId: 5, assignment: 'Test Project', subjectCode: 'TST1', number: 2, members: 3, averageGrade: 12 },
  ],
  firstYearOnly: false,
  filters: {
    academicYears: [2026, 2025],
    subjectEditions: [{ id: 11, subjectCode: 'TST1', subjectName: 'Test Programming', classSections: ['T1', 'T2'] }],
    groups: [{ id: 7, subjectEditionId: 11, assignment: 'Test Project', number: 1 }, { id: 8, subjectEditionId: 11, assignment: 'Test Project', number: 2 }],
  },
};
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

// answers GET /groups/analytics with whatever the test gives for the query
function backend(answer: (query: URLSearchParams) => AnalyticsData = () => analytics) {
  const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    if (url.pathname === '/api/groups/analytics') return reply(answer(url.searchParams));
    throw new Error(`Unexpected request: ${init?.method ?? 'GET'} ${url.pathname}`);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function open(path = '/analytics') {
  render(<StudyMatchProvider><MemoryRouter initialEntries={[path]}><StaffArea currentUser={{ number: 9, name: 'Test Staff', institutionalEmail: 'staff@school.pt', role: 'STAFF', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

const card = (title: string) => within(screen.getByText(title).closest('.sm-card') as HTMLElement);

// the charts animate in when seen, and jsdom has no IntersectionObserver
class NoIntersection { observe() {} unobserve() {} disconnect() {} takeRecords() { return []; } }

beforeEach(async () => { vi.stubGlobal('IntersectionObserver', NoIntersection); await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('view analytics (staff)', () => {
  it('shows the indicators, attendance per month, strongest traits and group balance', async () => {
    backend();
    open();

    expect(await screen.findByText('12')).toBeTruthy();
    expect(card('Active subjects').getByText('3')).toBeTruthy();
    expect(card('Average attendance').getByText('87')).toBeTruthy();
    // november's 84.8 is rounded
    expect(within(screen.getByRole('figure', { name: 'Attendance by month' })).getAllByText('85%').length).toBeGreaterThan(0);
    expect(screen.getByRole('figure', { name: 'Students by strongest trait' })).toBeTruthy();
    // each group is 1 point away from the 13 the groups average
    expect(within(screen.getByRole('figure', { name: 'Group balance' })).getAllByText('±1.0').length).toBeGreaterThan(1);
  });

  it("compares each group with its own assignment's groups, not with other assignments", async () => {
    backend(() => ({ ...analytics, groupBalance: [
      ...analytics.groupBalance,
      { groupId: 9, assignmentId: 6, assignment: 'Test Report', subjectCode: 'TST1', number: 1, members: 3, averageGrade: 18 },
      { groupId: 10, assignmentId: 6, assignment: 'Test Report', subjectCode: 'TST1', number: 2, members: 3, averageGrade: 18 },
    ] }));
    open();

    // the project's groups are 1 away from their 13, the report's are level at 18
    const chart = within(await screen.findByRole('figure', { name: 'Group balance' }));
    expect(chart.getAllByText('±1.0').length).toBeGreaterThan(1);
    expect(chart.getAllByText('±0.0').length).toBeGreaterThan(1);
    expect(chart.getAllByText('Test Report G1').length).toBeGreaterThan(0);
  });

  it('keeps two subjects\' assignments of the same name apart, labelled with the subject', async () => {
    backend(() => ({ ...analytics, groupBalance: [
      ...analytics.groupBalance,
      { groupId: 9, assignmentId: 6, assignment: 'Test Project', subjectCode: 'TST2', number: 1, members: 3, averageGrade: 18 },
      { groupId: 10, assignmentId: 6, assignment: 'Test Project', subjectCode: 'TST2', number: 2, members: 3, averageGrade: 18 },
    ] }));
    open();

    // one mean per assignment: TST1's groups are 1 from 13, TST2's are level at 18
    const chart = within(await screen.findByRole('figure', { name: 'Group balance' }));
    expect(chart.getAllByText('±1.0').length).toBeGreaterThan(1);
    expect(chart.getAllByText('±0.0').length).toBeGreaterThan(1);
    expect(chart.getAllByText('TST1 Test Project G1').length).toBeGreaterThan(0);
    expect(chart.getAllByText('TST2 Test Project G1').length).toBeGreaterThan(0);
  });

  it('asks again for the chosen subject, and only offers its classes and groups', async () => {
    const fetchMock = backend();
    open();

    const groupNames = () => within(screen.getByLabelText('Group')).getAllByRole('option').map(option => option.textContent);
    await screen.findByText('12');
    // every subject shown: the group names carry the subject
    expect(groupNames()).toContain('TST1 · Test Project · group 1');

    fireEvent.change(screen.getByLabelText('Subject'), { target: { value: '11' } });

    await waitFor(() => expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith('/groups/analytics?subjectEditionId=11'))).toBe(true));
    expect(groupNames()).toContain('Test Project · group 1');
    const classes = within(screen.getByLabelText('Class')).getAllByRole('option').map(option => option.textContent);
    expect(classes).toEqual(['All classes', 'T1', 'T2']);
    expect((screen.getByLabelText('Group') as HTMLSelectElement).disabled).toBe(false);
  });

  it('a new subject clears the class chosen for the old one', async () => {
    const fetchMock = backend();
    open('/analytics?subjectEditionId=11&classSection=T2');

    fireEvent.change(await screen.findByLabelText('Subject'), { target: { value: '' } });

    await waitFor(() => expect(fetchMock.mock.calls.at(-1)?.[0]).toMatch(/\/groups\/analytics$/));
  });

  it('suggests a broader selection when nobody is in it', async () => {
    backend(() => ({ ...analytics, totalStudents: 0, activeSubjects: 0, averageAttendance: null, attendanceOverTime: [], groupBalance: [],
      personalityDistribution: analytics.personalityDistribution.map(item => ({ ...item, students: 0 })) }));
    open('/analytics?subjectEditionId=11&classSection=T2');

    expect(await screen.findByText('No data for this selection')).toBeTruthy();
    expect(screen.getByText(/Try a broader selection/)).toBeTruthy();
    expect(screen.queryByText('Group balance')).toBeNull();
  });

  it('only first-year students: no grade history instead of the group balance', async () => {
    backend(() => ({ ...analytics, firstYearOnly: true }));
    open();

    expect(await screen.findByText('No grade history yet: only first-year students are selected.')).toBeTruthy();
    expect(screen.queryByText('±1.0')).toBeNull();
  });

  it('never shows a statute type', async () => {
    backend();
    open();

    await screen.findByText('12');
    expect(document.body.textContent).not.toMatch(/working student|statute holder/i);
  });
});
