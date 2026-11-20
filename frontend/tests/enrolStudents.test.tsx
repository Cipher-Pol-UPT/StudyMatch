import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { StaffArea } from '../src/pages/staff/StaffArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { EnrolmentResultData } from '../src/types/enrolments';
import type { StudentSummaryData, SubjectEditionData } from '../src/types/studentDirectory';

const edition: SubjectEditionData = { id: 7, academicYear: 2026, subject: { id: 3, code: 'CS101', name: 'Programming', ects: 6, category: { id: 1, name: 'Software' }, degrees: [] }, teachers: [], classSections: ['T1', 'T2'] };
const ana: StudentSummaryData = { number: 50001, name: 'Ana Silva', degree: 'Informatics', curricularYear: 2, attendance: null, averageGrade: null, personalityProfile: null, accountStatus: 'ACTIVE' };
const rui: StudentSummaryData = { ...ana, number: 50002, name: 'Rui Costa' };
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

interface Sent { path: string; method: string; body: unknown }
type Answer = (path: string) => Response | undefined;

// editions, a searchable student list, and whatever the test answers to the enrolment calls
function backend(editions: SubjectEditionData[], answer: Answer) {
  const sent: Sent[] = [];
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    const method = init?.method ?? 'GET';
    if (url.pathname === '/api/subject-editions/current') return reply(editions);
    if (url.pathname === '/api/students/') {
      const search = (url.searchParams.get('search') ?? '').toLowerCase();
      const rows = [ana, rui].filter(row => !search || row.name.toLowerCase().includes(search));
      return reply({ content: rows, page: 0, size: 20, totalElements: rows.length, totalPages: 1 });
    }
    const body = init?.body instanceof FormData ? init.body.get('file') : init?.body ? JSON.parse(String(init.body)) : null;
    sent.push({ path: url.pathname + url.search, method, body });
    const custom = answer(url.pathname);
    if (custom) return custom;
    throw new Error(`Unexpected request: ${url.pathname}`);
  }));
  return sent;
}

function open() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={['/enrolments']}><StaffArea currentUser={{ number: 9, name: 'Test Staff', institutionalEmail: 'staff@school.pt', role: 'STAFF', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

const tick = async (name: string) => fireEvent.click(await screen.findByRole('checkbox', { name }));

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('enrol students (staff)', () => {
  it('enrols the ticked students in the chosen class, keeping ticks across searches', async () => {
    const results: EnrolmentResultData[] = [
      { number: 50001, name: 'Ana Silva', result: 'ENROLLED' },
      { number: 50002, name: 'Rui Costa', result: 'ENROLLED' },
    ];
    const sent = backend([edition], path => path === '/api/subject-editions/7/enrolments' ? reply(results) : undefined);
    open();
    fireEvent.change(await screen.findByLabelText(i18n.t('directory.class')), { target: { value: 'T2' } });
    await tick('Ana Silva');
    fireEvent.change(screen.getByLabelText(i18n.t('directory.search')), { target: { value: 'rui' } });
    await waitFor(() => expect(screen.queryByRole('checkbox', { name: 'Ana Silva' })).toBeNull());
    await tick('Rui Costa');

    fireEvent.click(screen.getByRole('button', { name: i18n.t('enrolments.enrolSelected', { count: 2 }) }));

    expect(await screen.findByText(i18n.t('enrolments.summary', { enrolled: 2, skipped: 0 }))).toBeTruthy();
    expect(sent).toEqual([{ path: '/api/subject-editions/7/enrolments', method: 'POST', body: { classSection: 'T2', studentNumbers: [50001, 50002] } }]);
    expect(screen.queryByRole('table', { name: i18n.t('enrolments.skipped') })).toBeNull();
  });

  it('lists every skipped student with the reason', async () => {
    backend([edition], () => reply([
      { number: 50001, name: 'Ana Silva', result: 'ENROLLED' },
      { number: 50002, name: 'Rui Costa', result: 'ALREADY_PASSED' },
    ]));
    open();
    await tick('Ana Silva');
    await tick('Rui Costa');
    fireEvent.click(screen.getByRole('button', { name: i18n.t('enrolments.enrolSelected', { count: 2 }) }));

    const skipped = within(await screen.findByRole('table', { name: i18n.t('enrolments.skipped') }));
    expect(screen.getByText(i18n.t('enrolments.summary', { enrolled: 1, skipped: 1 }))).toBeTruthy();
    expect(skipped.getByText('Rui Costa')).toBeTruthy();
    expect(skipped.getByText(i18n.t('enrolments.results.ALREADY_PASSED'))).toBeTruthy();
    expect(skipped.queryByText('Ana Silva')).toBeNull();
  });

  it('imports a CSV into the chosen class and lists the rejected rows with their line', async () => {
    const sent = backend([edition], () => reply([
      { row: 2, number: 50001, name: 'Ana Silva', result: 'ENROLLED' },
      { row: 3, result: 'INVALID_NUMBER' },
      { row: 4, number: 99999, result: 'NOT_FOUND' },
    ]));
    open();
    fireEvent.click(await screen.findByRole('radio', { name: i18n.t('registration.csvImport') }));
    const file = new File(['studentNumber\n50001\nabc\n99999\n'], 'enrolments.csv', { type: 'text/csv' });
    fireEvent.change(document.querySelector('input[type="file"]') as HTMLInputElement, { target: { files: [file] } });

    const skipped = within(await screen.findByRole('table', { name: i18n.t('enrolments.skipped') }));
    expect(sent[0].path).toBe('/api/subject-editions/7/enrolments/import?classSection=T1');
    expect((sent[0].body as File).name).toBe('enrolments.csv');
    expect(skipped.getByText('3')).toBeTruthy();
    expect(skipped.getByText(i18n.t('enrolments.results.INVALID_NUMBER'))).toBeTruthy();
    expect(skipped.getByText(i18n.t('enrolments.results.NOT_FOUND'))).toBeTruthy();
    expect(screen.getByText(i18n.t('enrolments.summary', { enrolled: 1, skipped: 2 }))).toBeTruthy();
  });

  it('shows a refused CSV in the banner and enrols nobody', async () => {
    backend([edition], () => reply({ code: 'CSV_MISSING_COLUMNS', params: { columns: 'studentNumber' } }, 400));
    open();
    fireEvent.click(await screen.findByRole('radio', { name: i18n.t('registration.csvImport') }));
    fireEvent.change(document.querySelector('input[type="file"]') as HTMLInputElement, { target: { files: [new File(['number\n1\n'], 'wrong.csv', { type: 'text/csv' })] } });

    expect(await screen.findByText(i18n.t('errors.CSV_MISSING_COLUMNS', { columns: 'studentNumber' }))).toBeTruthy();
    expect(screen.queryByText(i18n.t('enrolments.done'))).toBeNull();
  });

  it('shows an edition that is no longer current in the banner', async () => {
    backend([edition], () => reply({ code: 'SUBJECT_EDITION_NOT_CURRENT' }, 409));
    open();
    await tick('Ana Silva');
    fireEvent.click(screen.getByRole('button', { name: i18n.t('enrolments.enrolSelected', { count: 1 }) }));

    expect(await screen.findByText(i18n.t('errors.SUBJECT_EDITION_NOT_CURRENT'))).toBeTruthy();
  });

  it('says so when there are no editions this year', async () => {
    backend([], () => undefined);
    open();

    expect(await screen.findByText(i18n.t('enrolments.noEditions'))).toBeTruthy();
  });
});
