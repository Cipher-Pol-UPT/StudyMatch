import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { GradeColumn, GradeSheetData } from '../src/types/grades';
import type { EditionStudentData, SubjectEditionData } from '../src/types/studentDirectory';

const edition: SubjectEditionData = { id: 7, academicYear: 2026, subject: { id: 3, code: 'SQ', name: 'Software Quality', ects: 6, category: { id: 1, name: 'Software' }, degrees: [] }, teachers: [{ number: 9, name: 'Teacher' }], classSections: ['A'] };
const enrolled: EditionStudentData[] = [
  { number: 50001, name: 'Ana Silva', classSection: 'A', personalityProfile: null, categoryLevel: null, attendance: null },
  { number: 50002, name: 'Rui Costa', classSection: 'A', personalityProfile: null, categoryLevel: null, attendance: null },
];
const project: GradeColumn = { id: 1, name: 'Project 1', type: 'PROJECT', weight: 70 };
const sheet = (assignments: GradeColumn[]): GradeSheetData => ({ assignments, students: [], weightsComplete: false });
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

// the grade sheet answers in order, so the second one can include the new assignment
function backend(sheets: GradeSheetData[], onCreate: () => Response) {
  const created: unknown[] = [];
  let reads = 0;
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    if (url.pathname === '/api/subject-editions/') return reply([edition]);
    if (url.pathname === '/api/subject-editions/7/students') return reply(enrolled);
    if (url.pathname === '/api/subject-editions/7/grades') return reply(sheets[Math.min(reads++, sheets.length - 1)]);
    if (url.pathname === '/api/subject-editions/7/assignments' && init?.method === 'POST') {
      created.push(JSON.parse(String(init.body)));
      return onCreate();
    }
    throw new Error(`Unexpected request: ${url.pathname}`);
  }));
  return created;
}

async function openForm() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={['/subject-editions/7/assignments']}><TeacherArea currentUser={{ number: 9, name: 'Teacher', institutionalEmail: 'teacher@school.pt', role: 'TEACHER', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
  const newButton = await screen.findByRole('button', { name: i18n.t('assignments.new') });
  await waitFor(() => expect((newButton as HTMLButtonElement).disabled).toBe(false));
  fireEvent.click(newButton);
  const dialog = within(await screen.findByRole('dialog'));
  fireEvent.change(dialog.getByLabelText(i18n.t('assignments.name')), { target: { value: 'Final exam' } });
  fireEvent.change(dialog.getByLabelText(i18n.t('assignments.date')), { target: { value: '2099-12-18' } });
  return dialog;
}

const create = (dialog: ReturnType<typeof within>) => fireEvent.click(dialog.getByRole('button', { name: i18n.t('assignments.create') }));
const weight = (dialog: ReturnType<typeof within>) => dialog.getByRole('spinbutton', { name: i18n.t('assignments.weight') });

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('create an assignment (teacher)', () => {
  it('creates an individual exam, with group work locked, and lists it', async () => {
    const exam: GradeColumn = { id: 2, name: 'Final exam', type: 'EXAM', weight: 30 };
    const created = backend([sheet([project]), sheet([project, exam])], () => reply({ ...exam, date: '2099-12-18', grouping: null }, 201));
    const dialog = await openForm();
    fireEvent.click(dialog.getByRole('radio', { name: i18n.t('assignments.types.EXAM') }));

    expect((dialog.getByRole('switch', { name: i18n.t('assignments.groupWork') }) as HTMLButtonElement).disabled).toBe(true);
    expect(dialog.getByText(i18n.t('assignments.alwaysIndividual'))).toBeTruthy();
    expect(dialog.getByText(i18n.t('assignments.weightLeft', { remaining: 30 }))).toBeTruthy();
    create(dialog);

    expect(await screen.findByText(i18n.t('assignments.created', { name: 'Final exam' }))).toBeTruthy();
    expect(created).toEqual([{ name: 'Final exam', type: 'EXAM', date: '2099-12-18', weight: 30, grouping: null }]);
    expect(await screen.findByRole('cell', { name: 'Final exam' })).toBeTruthy();
    expect(screen.getByText(i18n.t('assignments.weightsTotal', { total: 100 }))).toBeTruthy();
  });

  it('creates a group project with its sizes, same class and a pair kept apart', async () => {
    const created = backend([sheet([])], () => reply({ id: 2, name: 'Final exam', type: 'PROJECT', date: '2099-12-18', weight: 30, grouping: null }, 201));
    const dialog = await openForm();
    fireEvent.click(dialog.getByRole('switch', { name: i18n.t('assignments.groupWork') }));
    fireEvent.click(dialog.getByRole('switch', { name: i18n.t('assignments.sameClass') }));
    fireEvent.click(dialog.getByRole('button', { name: i18n.t('assignments.addPair') }));
    fireEvent.change(dialog.getByLabelText(i18n.t('assignments.firstStudent')), { target: { value: '50001' } });
    fireEvent.change(dialog.getByLabelText(i18n.t('assignments.secondStudent')), { target: { value: '50002' } });
    fireEvent.click(dialog.getByRole('button', { name: i18n.t('assignments.addPair') }));

    expect(dialog.getByText('Ana Silva ↔ Rui Costa')).toBeTruthy();
    create(dialog);

    await waitFor(() => expect(created).toHaveLength(1));
    expect(created[0]).toEqual({ name: 'Final exam', type: 'PROJECT', date: '2099-12-18', weight: 30, grouping: {
      minSize: 3, optimalSize: 4, maxSize: 5, sameClass: true, separations: [{ firstStudent: 50001, secondStudent: 50002 }],
    } });
  });

  it('says how much weight is left instead of sending too much, and shows the backend weight error there too', async () => {
    const created = backend([sheet([project])], () => reply({ code: 'ASSIGNMENT_WEIGHT_EXCEEDED', params: { remaining: 10 } }, 409));
    const dialog = await openForm();
    fireEvent.keyDown(weight(dialog), { key: 'End' });
    create(dialog);

    expect(dialog.getByText(i18n.t('assignments.weightOver', { remaining: 30 }))).toBeTruthy();
    expect(created).toEqual([]);

    const button = dialog.getByRole('button', { name: i18n.t('assignments.create') }) as HTMLButtonElement;
    await waitFor(() => expect(button.disabled).toBe(false));
    fireEvent.keyDown(weight(dialog), { key: 'Home' });
    create(dialog);

    expect(await dialog.findByText(i18n.t('errors.ASSIGNMENT_WEIGHT_EXCEEDED', { remaining: 10 }))).toBeTruthy();
    expect(created).toHaveLength(1);
  });

  it('shows wrong group sizes under the groups', async () => {
    backend([sheet([])], () => reply({ code: 'INVALID_GROUP_SIZES' }, 400));
    const dialog = await openForm();
    fireEvent.click(dialog.getByRole('switch', { name: i18n.t('assignments.groupWork') }));
    create(dialog);

    const groups = within(dialog.getByRole('group', { name: i18n.t('assignments.groups') }));
    expect(await groups.findByText(i18n.t('errors.INVALID_GROUP_SIZES'))).toBeTruthy();
  });
});
