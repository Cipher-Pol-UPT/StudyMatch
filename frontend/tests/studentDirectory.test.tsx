import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { StaffArea } from '../src/pages/staff/StaffArea';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { EditionStudentData, StudentRecordData, StudentSummaryData, SubjectEditionData } from '../src/types/studentDirectory';
import type { StudentProfileData } from '../src/types/studentProfile';

const ana: StudentSummaryData = { number: 50001, name: 'Ana Silva', degree: 'Informatics', curricularYear: 2, attendance: 50, averageGrade: 0, personalityProfile: { leadership: 0.8, problemSolving: 0.6, creativity: 0.4, execution: 0.7 }, accountStatus: 'ACTIVE' };
const rui: StudentSummaryData = { ...ana, number: 50002, name: 'Rui Costa', curricularYear: 1, attendance: null, averageGrade: null, personalityProfile: null, accountStatus: 'BLOCKED' };
const profile: StudentProfileData = { number: ana.number, name: ana.name, banner: null, pfp: null, aboutMe: 'Learning together.', skills: [], interests: [], languages: ['PORTUGUESE'], tutorBadges: [], achievements: [], privateProfile: { institutionalEmail: 'ana@school.pt', degree: 'Informatics', curricularYear: 2, personalityProfile: ana.personalityProfile, attendance: 99, hasAcademicHistory: true } };
const record: StudentRecordData = { profile: { ...profile, privateProfile: { ...profile.privateProfile!, attendance: null } }, personalEmail: 'ana.personal@example.com', accountStatus: 'ACTIVE', statuteType: 'WORKING_STUDENT', statuteEndDate: '2030-09-01', averageGrade: 14, categoryLevels: [{ categoryId: 1, category: 'Programming', level: 14 }], enrolments: [{ subjectCode: 'CS101', subjectName: 'Programming', ects: 6, academicYear: 2026, classSection: 'T1', status: 'PASSED', finalGrade: 14 }] };
const edition: SubjectEditionData = { id: 7, academicYear: 2026, subject: { id: 3, code: 'CS101', name: 'Programming', ects: 6, category: { id: 1, name: 'Software' }, degrees: [] }, teachers: [{ number: 9, name: 'Teacher' }], classSections: ['T1', 'T2'] };
const enrolled: EditionStudentData[] = [
  { number: ana.number, name: ana.name, classSection: 'T1', personalityProfile: ana.personalityProfile, categoryLevel: 14, attendance: 50 },
  { number: rui.number, name: rui.name, classSection: 'T2', personalityProfile: null, categoryLevel: null, attendance: null },
];
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
type Override = (url: URL) => Response | undefined | Promise<Response | undefined>;

function backend(override?: Override) {
  const fetchMock = vi.fn<typeof fetch>(async input => {
    const url = new URL(String(input), 'https://test.invalid');
    const custom = await override?.(url);
    if (custom) return custom;
    if (url.pathname === '/api/degrees/') return reply([{ id: 5, name: 'Informatics', curricularYears: 3 }]);
    if (url.pathname === '/api/subject-editions/') return reply([edition]);
    if (url.pathname === '/api/students/') {
      const search = (url.searchParams.get('search') ?? '').toLowerCase();
      const rows = [ana, rui].filter(row => (!search || row.name.toLowerCase().includes(search) || String(row.number) === search)
        && (!url.searchParams.get('status') || row.accountStatus === url.searchParams.get('status')));
      return reply({ content: rows, page: Number(url.searchParams.get('page') ?? 0), size: 20, totalElements: rows.length, totalPages: rows.length ? 1 : 0 });
    }
    if (url.pathname === '/api/students/50001') return reply(record);
    if (url.pathname === '/api/students/50001/profile') return reply(profile);
    if (url.pathname === '/api/subject-editions/7/students') {
      const search = (url.searchParams.get('search') ?? '').toLowerCase();
      return reply(enrolled.filter(row => (!search || row.name.toLowerCase().includes(search) || String(row.number) === search)
        && (!url.searchParams.get('classSection') || row.classSection === url.searchParams.get('classSection'))));
    }
    throw new Error(`Unexpected request: ${url.pathname}`);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function open(path = '/students', role: 'STAFF' | 'TEACHER' = 'STAFF') {
  const Area = role === 'STAFF' ? StaffArea : TeacherArea;
  render(<StudyMatchProvider><MemoryRouter initialEntries={[path]}><Area currentUser={{ number: 9, name: 'Test User', institutionalEmail: 'test@school.pt', role, mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('staff student directory', () => {
  it('loads real rows with all the requested evidence and preserves zero grades', async () => {
    const fetchMock = backend();
    open();
    const name = await screen.findByRole('link', { name: 'Ana Silva' });
    const row = within(name.closest('tr')!);
    for (const value of ['50001', 'Informatics', '2', '50%', '0 / 20', 'Leadership', '80%']) expect(row.getByText(value)).toBeTruthy();
    expect(row.getByText(i18n.t('accountStatuses.ACTIVE'))).toBeTruthy();
    expect(screen.getByText('No grades yet')).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith('/api/students/?page=0', expect.objectContaining({ credentials: 'include', method: 'GET' }));
  });

  it('sends combined filters to the backend, resets the page and offers clearing on no matches', async () => {
    const fetchMock = backend();
    open('/students?page=1');
    await screen.findByRole('link', { name: 'Ana Silva' });
    fireEvent.change(screen.getByLabelText('Search by name or number'), { target: { value: 'Ana & Rui' } });
    fireEvent.change(screen.getByLabelText('Degree'), { target: { value: '5' } });
    fireEvent.change(screen.getByLabelText('Curricular year'), { target: { value: '2' } });
    fireEvent.change(screen.getByLabelText('Account status'), { target: { value: 'BLOCKED' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    await screen.findByText('No students match these filters');
    const url = new URL(String(fetchMock.mock.calls.filter(([url]) => String(url).startsWith('/api/students/?')).at(-1)![0]), 'https://test.invalid');
    expect(Object.fromEntries(url.searchParams)).toEqual({ search: 'Ana & Rui', degreeId: '5', curricularYear: '2', status: 'BLOCKED', page: '0' });
    fireEvent.click(screen.getAllByRole('button', { name: 'Clear filters' })[0]);
    expect(await screen.findByRole('link', { name: 'Ana Silva' })).toBeTruthy();
  });

  it('paginates without losing the degree filter', async () => {
    const fetchMock = backend(url => {
      if (url.pathname !== '/api/students/') return;
      const page = Number(url.searchParams.get('page') ?? 0);
      const content = page === 1 ? [rui] : [ana, ...Array.from({ length: 19 }, (_, i) => ({ ...rui, number: 60000 + i, name: `Classmate ${i}` }))];
      return reply({ content, page, size: 20, totalElements: 21, totalPages: 2 });
    });
    open('/students?degreeId=5');
    await screen.findByRole('link', { name: 'Ana Silva' });
    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    await screen.findByRole('link', { name: 'Rui Costa' });
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('degreeId=5&page=1'))).toBe(true);
    expect((screen.getByRole('button', { name: 'Next page' }) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: 'Previous page' }));
    expect(await screen.findByRole('link', { name: 'Ana Silva' })).toBeTruthy();
  });

  it('opens the staff-only full record and preserves filters on return', async () => {
    backend();
    open('/students?degreeId=5&search=Ana');
    fireEvent.click(await screen.findByRole('link', { name: 'Ana Silva' }));
    expect(await screen.findByText('ana.personal@example.com')).toBeTruthy();
    expect(screen.getByText(i18n.t('statutes.WORKING_STUDENT'))).toBeTruthy();
    expect(screen.getByText('2030-09-01')).toBeTruthy();
    expect(screen.getByText('CS101 · Programming')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Edit profile' })).toBeNull();
    const back = new URL(screen.getByRole('link', { name: /Back to students/ }).getAttribute('href')!, 'https://test.invalid');
    expect(back.searchParams.get('degreeId')).toBe('5');
    expect(back.searchParams.get('search')).toBe('Ana');
  });

  it('shows the unfiltered empty state and recovers from a server error', async () => {
    let fail = true;
    backend(url => url.pathname === '/api/students/' ? fail ? reply({}, 500) : reply({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }) : undefined);
    open();
    await screen.findByText('Something went wrong. Please try again.');
    fail = false;
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('No students registered')).toBeTruthy();
    expect(screen.queryByText('No students match these filters')).toBeNull();
  });

  it('does not replace new filter results with an older request that finishes late', async () => {
    let release: (response: Response) => void = () => undefined;
    let oldRequest = true;
    backend(url => {
      if (url.pathname === '/api/students/' && oldRequest) {
        oldRequest = false;
        return new Promise<Response>(resolve => { release = resolve; });
      }
    });
    open();
    await screen.findByRole('option', { name: 'Informatics' });
    fireEvent.change(screen.getByLabelText('Search by name or number'), { target: { value: 'Rui' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    await screen.findByRole('link', { name: 'Rui Costa' });
    await act(async () => release(reply({ content: [ana], page: 0, size: 20, totalElements: 1, totalPages: 1 })));
    expect(screen.queryByRole('link', { name: 'Ana Silva' })).toBeNull();
    expect(screen.getByRole('link', { name: 'Rui Costa' })).toBeTruthy();
  });
});

describe('teacher student directory', () => {
  it('opens students from a taught subject and filters by name and class', async () => {
    const fetchMock = backend();
    open('/subjects', 'TEACHER');
    fireEvent.click(await screen.findByRole('link', { name: /Programming/ }));
    await screen.findByRole('link', { name: 'Ana Silva' });
    fireEvent.change(screen.getByLabelText('Search by name or number'), { target: { value: 'Rui' } });
    fireEvent.change(screen.getByLabelText('Class'), { target: { value: 'T2' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    expect(await screen.findByRole('link', { name: 'Rui Costa' })).toBeTruthy();
    await waitFor(() => expect(screen.queryByRole('link', { name: 'Ana Silva' })).toBeNull());
    expect(fetchMock).toHaveBeenCalledWith('/api/subject-editions/7/students?search=Rui&classSection=T2', expect.objectContaining({ method: 'GET' }));
    expect(fetchMock.mock.calls.some(([url]) => String(url).startsWith('/api/students/?'))).toBe(false);
  });

  it('shows a read-only profile with attendance from this subject and no staff-only request', async () => {
    const fetchMock = backend();
    open('/subject-editions/7/students', 'TEACHER');
    fireEvent.click(await screen.findByRole('link', { name: 'Ana Silva' }));
    expect(await screen.findByRole('heading', { name: 'Ana Silva' })).toBeTruthy();
    expect(screen.getAllByText('50%')).toHaveLength(2);
    expect(screen.queryByText('99%')).toBeNull();
    expect(screen.queryByText('ana.personal@example.com')).toBeNull();
    expect(screen.queryByText(i18n.t('statutes.WORKING_STUDENT'))).toBeNull();
    expect(screen.queryByRole('button', { name: 'Edit profile' })).toBeNull();
    expect(fetchMock).toHaveBeenCalledWith('/api/students/50001/profile', expect.objectContaining({ method: 'GET' }));
    expect(fetchMock.mock.calls.some(([url]) => String(url) === '/api/students/50001')).toBe(false);
  });

  it('rechecks enrolment before loading a profile on a direct link', async () => {
    const fetchMock = backend(url => url.pathname === '/api/subject-editions/7/students' ? reply([]) : undefined);
    open('/subject-editions/7/students/50001', 'TEACHER');
    expect(await screen.findByText(i18n.t('errors.NOT_FOUND'))).toBeTruthy();
    expect(fetchMock.mock.calls.some(([url]) => String(url) === '/api/students/50001/profile')).toBe(false);
  });

  it('does not request students of an edition outside the teacher list', async () => {
    const fetchMock = backend();
    open('/subject-editions/999/students', 'TEACHER');
    expect(await screen.findByText(i18n.t('errors.NOT_FOUND'))).toBeTruthy();
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/999/students'))).toBe(false);
  });

  it('distinguishes no enrolments from no matches and clears the filters', async () => {
    backend(url => url.pathname === '/api/subject-editions/7/students' ? reply([]) : undefined);
    open('/subject-editions/7/students', 'TEACHER');
    expect(await screen.findByText('No students enrolled')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Search by name or number'), { target: { value: 'Nobody' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    expect(await screen.findByText('No students match these filters')).toBeTruthy();
    fireEvent.click(screen.getAllByRole('button', { name: 'Clear filters' })[0]);
    expect(await screen.findByText('No students enrolled')).toBeTruthy();
  });

  it('shows an empty state when no subject editions are assigned', async () => {
    backend(url => url.pathname === '/api/subject-editions/' ? reply([]) : undefined);
    open('/subjects', 'TEACHER');
    expect(await screen.findByText('No subject editions assigned')).toBeTruthy();
  });

  it('shows backend access errors instead of treating them as an empty list', async () => {
    backend(url => url.pathname === '/api/subject-editions/7/students' ? reply({ code: 'ACCESS_DENIED' }, 403) : undefined);
    open('/subject-editions/7/students', 'TEACHER');
    expect(await screen.findByText(i18n.t('errors.ACCESS_DENIED'))).toBeTruthy();
    expect(screen.queryByText('No students enrolled')).toBeNull();
  });
});
