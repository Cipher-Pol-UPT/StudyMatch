import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { StaffArea } from '../src/pages/staff/StaffArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { AttendanceRow, AttendanceSheetData } from '../src/types/attendance';
import type { SubjectEditionData } from '../src/types/studentDirectory';

const edition: SubjectEditionData = { id: 7, academicYear: 2026, subject: { id: 3, code: 'SQ', name: 'Software Quality', ects: 6, category: { id: 1, name: 'Software' }, degrees: [] }, teachers: [], classSections: ['A', 'B'] };
const row = (number: number, name: string, extra: Partial<AttendanceRow> = {}): AttendanceRow =>
  ({ number, name, absent: false, justified: false, attendance: 1, statute: false, ...extra });
const STARTS_AT = '2026-11-19T09:00:00';
const sheet = (students: AttendanceRow[], recorded = false): AttendanceSheetData => ({ classSection: 'B', startsAt: STARTS_AT, recorded, students });
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

interface Sent { path: string; method: string; body: unknown }

// the editions, then whatever the test answers to the sheet (GET) and to saving it (PUT)
function backend(onGet: () => Response, onPut: () => Response = () => reply({})) {
  const sent: Sent[] = [];
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    const method = init?.method ?? 'GET';
    if (url.pathname === '/api/subject-editions/current') return reply([edition]);
    sent.push({ path: url.pathname + url.search, method, body: init?.body ? JSON.parse(String(init.body)) : null });
    if (url.pathname === '/api/subject-editions/7/attendance') return method === 'PUT' ? onPut() : onGet();
    throw new Error(`Unexpected request: ${url.pathname}`);
  }));
  return sent;
}

async function openLesson() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={['/attendance']}><StaffArea currentUser={{ number: 9, name: 'Test Staff', institutionalEmail: 'staff@school.pt', role: 'STAFF', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
  fireEvent.change(await screen.findByLabelText(i18n.t('directory.class')), { target: { value: 'B' } });
  fireEvent.change(screen.getByLabelText(i18n.t('rooms.date')), { target: { value: '2026-11-19' } });
  fireEvent.change(screen.getByLabelText(i18n.t('rooms.start')), { target: { value: '09:00' } });
}

const absentBox = (name: string) => screen.findByRole('checkbox', { name: i18n.t('attendance.absentLabel', { name }) });
const justifiedSwitch = (name: string) => screen.getByRole('switch', { name: i18n.t('attendance.justifiedLabel', { name }) });

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('register attendance (staff)', () => {
  it('registers who was absent in the chosen lesson, justified only once absent is ticked', async () => {
    const students = [row(50001, 'Ana Silva'), row(50002, 'Beatriz Nunes'), row(50007, 'Marta Lopes')];
    const sent = backend(() => reply(sheet(students)), () => reply(sheet([students[0], { ...students[1], absent: true }, { ...students[2], absent: true, justified: true }], true)));
    await openLesson();

    expect(screen.queryByText(i18n.t('attendance.recordedTitle'))).toBeNull();
    fireEvent.click(await absentBox('Beatriz Nunes'));
    expect((justifiedSwitch('Ana Silva') as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(await absentBox('Marta Lopes'));
    fireEvent.click(justifiedSwitch('Marta Lopes'));
    fireEvent.click(screen.getByRole('button', { name: i18n.t('attendance.save', { count: 2 }) }));

    expect(await screen.findByText(i18n.t('attendance.saved', { count: 2 }))).toBeTruthy();
    expect(sent[0]).toEqual({ path: `/api/subject-editions/7/attendance?classSection=B&startsAt=${encodeURIComponent(STARTS_AT)}`, method: 'GET', body: null });
    expect(sent[1]).toEqual({ path: '/api/subject-editions/7/attendance', method: 'PUT', body: {
      classSection: 'B', startsAt: STARTS_AT, absences: [{ studentNumber: 50002, justified: false }, { studentNumber: 50007, justified: true }],
    } });
    expect(screen.getByText(i18n.t('attendance.recordedTitle'))).toBeTruthy();
  });

  it('loads a lesson that already has a record to correct it, and unticking absent clears justified', async () => {
    const sent = backend(() => reply(sheet([row(50001, 'Ana Silva', { absent: true, justified: true }), row(50002, 'Beatriz Nunes')], true)),
      () => reply(sheet([row(50001, 'Ana Silva'), row(50002, 'Beatriz Nunes')], true)));
    await openLesson();

    expect(await screen.findByText(i18n.t('attendance.recordedTitle'))).toBeTruthy();
    expect((await absentBox('Ana Silva') as HTMLInputElement).checked).toBe(true);
    expect(justifiedSwitch('Ana Silva').getAttribute('aria-checked')).toBe('true');
    fireEvent.click(await absentBox('Ana Silva'));
    expect(justifiedSwitch('Ana Silva').getAttribute('aria-checked')).toBe('false');
    fireEvent.click(screen.getByRole('button', { name: i18n.t('attendance.save', { count: 0 }) }));

    expect(await screen.findByText(i18n.t('attendance.saved', { count: 0 }))).toBeTruthy();
    expect((sent[1].body as { absences: unknown[] }).absences).toEqual([]);
  });

  it('says under the date that a future lesson cannot be registered', async () => {
    backend(() => reply({ code: 'LESSON_IN_FUTURE' }, 400));
    await openLesson();

    expect(await screen.findByText(i18n.t('errors.LESSON_IN_FUTURE'))).toBeTruthy();
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByRole('button', { name: /Save attendance/ })).toBeNull();
  });

  it('shows a special statute student as not applicable, with nothing to mark', async () => {
    backend(() => reply(sheet([row(50001, 'Ana Silva'), row(50005, 'Rui Costa', { statute: true, attendance: null })])));
    await openLesson();

    const rui = within((await screen.findByText('Rui Costa')).closest('tr') as HTMLElement);
    expect(rui.getByText(i18n.t('attendance.statute'))).toBeTruthy();
    expect(rui.queryByRole('checkbox')).toBeNull();
    expect(rui.queryByRole('switch')).toBeNull();
    expect(await absentBox('Ana Silva')).toBeTruthy();
  });

  it('shows an edition that is no longer current in the banner', async () => {
    backend(() => reply(sheet([row(50001, 'Ana Silva')])), () => reply({ code: 'SUBJECT_EDITION_NOT_CURRENT' }, 409));
    await openLesson();
    fireEvent.click(await absentBox('Ana Silva'));
    fireEvent.click(screen.getByRole('button', { name: i18n.t('attendance.save', { count: 1 }) }));

    expect(await screen.findByText(i18n.t('errors.SUBJECT_EDITION_NOT_CURRENT'))).toBeTruthy();
  });
});
