import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { GradeColumn, GradeRow, GradeSheetData } from '../src/types/grades';
import type { SubjectEditionData } from '../src/types/studentDirectory';

const edition: SubjectEditionData = { id: 7, academicYear: 2026, subject: { id: 3, code: 'SQ', name: 'Software Quality', ects: 6, category: { id: 1, name: 'Software' }, degrees: [] }, teachers: [{ number: 9, name: 'Teacher' }], classSections: ['A'] };
const project: GradeColumn = { id: 1, name: 'Project 1', type: 'PROJECT', weight: 60 };
const exam: GradeColumn = { id: 2, name: 'Exam', type: 'EXAM', weight: 40 };
const ana: GradeRow = { number: 50001, name: 'Ana Silva', classSection: 'A', grades: { 1: 16 }, finalGrade: null, status: 'IN_PROGRESS' };
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

interface Sent { path: string; body: unknown }

function backend(sheet: GradeSheetData, onGrade: () => Response) {
  const sent: Sent[] = [];
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    if (url.pathname === '/api/subject-editions/') return reply([edition]);
    if (url.pathname === '/api/subject-editions/7/grades') return reply(sheet);
    if (init?.method === 'PUT') {
      sent.push({ path: url.pathname, body: JSON.parse(String(init.body)) });
      return onGrade();
    }
    throw new Error(`Unexpected request: ${url.pathname}`);
  }));
  return sent;
}

function open() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={['/subject-editions/7/grades']}><TeacherArea currentUser={{ number: 9, name: 'Teacher', institutionalEmail: 'teacher@school.pt', role: 'TEACHER', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

// clicks the cell, types the grade and presses Enter
async function type(assignment: string, value: string) {
  const label = i18n.t('grades.cellLabel', { name: 'Ana Silva', assignment });
  fireEvent.click(await screen.findByRole('button', { name: new RegExp(`^${label}`) }));
  const field = screen.getByRole('textbox', { name: label });
  fireEvent.change(field, { target: { value } });
  fireEvent.keyDown(field, { key: 'Enter' });
}

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('grade students (teacher)', () => {
  it('grades an empty cell straight away and shows the final grade once every assignment has one', async () => {
    const sent = backend({ assignments: [project, exam], students: [ana], weightsComplete: true },
      () => reply({ student: { ...ana, grades: { 1: 16, 2: 14.5 }, finalGrade: 15, status: 'PASSED' }, weightsComplete: true }));
    open();
    await type('Exam', '14,5');

    const row = within((await screen.findByText(i18n.t('enrolmentStatuses.PASSED'))).closest('tr') as HTMLElement);
    expect(sent).toEqual([{ path: '/api/subject-editions/7/assignments/2/grades/50001', body: { value: 14.5 } }]);
    expect(row.getByText('15')).toBeTruthy();
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('asks before changing a grade that was already there', async () => {
    const sent = backend({ assignments: [project, exam], students: [ana], weightsComplete: true },
      () => reply({ student: { ...ana, grades: { 1: 18 } }, weightsComplete: true }));
    open();
    await type('Project 1', '18');

    const dialog = within(await screen.findByRole('dialog'));
    expect(dialog.getByText(i18n.t('grades.changeTitle', { name: 'Ana Silva' }))).toBeTruthy();
    expect(dialog.getByText(i18n.t('grades.changeBody', { assignment: 'Project 1', from: 16, to: 18 }))).toBeTruthy();
    expect(sent).toEqual([]);
    fireEvent.click(dialog.getByRole('button', { name: i18n.t('grades.change') }));

    await waitFor(() => expect(sent).toEqual([{ path: '/api/subject-editions/7/assignments/1/grades/50001', body: { value: 18 } }]));
    expect(await screen.findByRole('button', { name: `${i18n.t('grades.cellLabel', { name: 'Ana Silva', assignment: 'Project 1' })}: 18` })).toBeTruthy();
  });

  it('refuses a grade outside 0 to 20 under the cell, without sending it', async () => {
    const sent = backend({ assignments: [project, exam], students: [ana], weightsComplete: true }, () => reply({}));
    open();
    await type('Exam', '21');

    expect(await screen.findByText(i18n.t('grades.range'))).toBeTruthy();
    expect(sent).toEqual([]);
  });

  it('warns that final grades wait while the weights are below 100%', async () => {
    backend({ assignments: [{ ...project, weight: 80 }], students: [ana], weightsComplete: false }, () => reply({}));
    open();

    expect(await screen.findByText(i18n.t('grades.incompleteTitle', { total: 80 }))).toBeTruthy();
    expect(screen.getByText(i18n.t('grades.incompleteBody'))).toBeTruthy();
  });

  it('tells in a toast when the student is no longer enrolled', async () => {
    backend({ assignments: [project, exam], students: [ana], weightsComplete: true },
      () => reply({ code: 'STUDENT_NOT_ENROLLED', params: { number: 50001 } }, 404));
    open();
    await type('Exam', '12');

    expect(await screen.findByText(i18n.t('errors.STUDENT_NOT_ENROLLED', { number: 50001 }))).toBeTruthy();
  });
});
