import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { StaffSubjectsPage } from '../src/pages/staff/StaffSubjectsPage';
import { StudyMatchProvider } from '../src/studymatch-ui';

const mocks = vi.hoisted(() => ({
  degrees: [{ id: 1, name: 'Computer Science', curricularYears: 3 }],
  categories: [{ id: 1, name: 'Programming' }],
  teachers: [{ number: 100, name: 'Teacher One', institutionalEmail: 't@studymatch.com' }],
  registerSubject: vi.fn(),
  registerEdition: vi.fn(),
}));

vi.mock('../src/api/subjects', () => ({
  getSubjects: vi.fn(() => Promise.resolve([])),
  registerSubject: mocks.registerSubject,
  registerEdition: mocks.registerEdition,
}));
vi.mock('../src/api/degrees', () => ({ getDegrees: vi.fn(() => Promise.resolve(mocks.degrees)) }));
vi.mock('../src/api/categories', () => ({ getCategories: vi.fn(() => Promise.resolve(mocks.categories)) }));
vi.mock('../src/api/teachers', () => ({ getTeachers: vi.fn(() => Promise.resolve(mocks.teachers)) }));
vi.mock('../src/api/academicYears', () => ({ getAcademicYears: vi.fn(() => Promise.resolve([{ startYear: 2026 }])) }));

const created = {
  id: 1, academicYear: 2026,
  subject: { id: 1, code: 'MAT1', name: 'Maths', ects: 6, category: { id: 1, name: 'Programming' },
    degrees: [{ degreeId: 1, degreeName: 'Computer Science', curricularYear: 1, semester: 1 }] },
  teachers: [{ number: 100, name: 'Teacher One' }], classSections: [],
};

function renderPage() {
  render(<StudyMatchProvider><StaffSubjectsPage /></StudyMatchProvider>);
}

async function openForm() {
  fireEvent.click(await screen.findByRole('button', { name: 'New subject' }));
  return screen.getByRole('dialog', { name: 'New subject' });
}

async function fillSubject(dialog: HTMLElement) {
  fireEvent.change(within(dialog).getByLabelText('Code'), { target: { value: 'MAT1' } });
  fireEvent.change(within(dialog).getByLabelText('Name'), { target: { value: 'Maths' } });
  await within(dialog).findByRole('option', { name: 'Programming' });
  fireEvent.change(within(dialog).getByLabelText('Category'), { target: { value: '1' } });
  await within(dialog).findByRole('option', { name: 'Computer Science' });
  fireEvent.change(within(dialog).getByLabelText('Degree'), { target: { value: '1' } });
  await within(dialog).findByRole('option', { name: 'Teacher One' });
  fireEvent.change(within(dialog).getByLabelText('Add a teacher'), { target: { value: '100' } });
}

afterEach(() => {
  cleanup();
  mocks.registerSubject.mockReset();
  mocks.registerEdition.mockReset();
});
beforeEach(async () => { await i18n.changeLanguage('en'); });

describe('register a subject', () => {
  it('sends the subject with its degrees and teachers, then shows the toast and the list', async () => {
    mocks.registerSubject.mockResolvedValue(created);
    renderPage();
    const dialog = await openForm();

    await fillSubject(dialog);
    fireEvent.click(within(dialog).getByRole('button', { name: 'Register subject' }));

    await waitFor(() => expect(mocks.registerSubject).toHaveBeenCalledWith({
      code: 'MAT1', name: 'Maths', ects: 6, categoryId: 1,
      degrees: [{ degreeId: 1, curricularYear: 1, semester: 1 }],
      edition: { academicYear: 2026, teacherNumbers: [100], classSections: [] },
    }));
    expect(await screen.findByText('Subject Maths registered.')).toBeTruthy();
    expect(await screen.findByRole('cell', { name: 'MAT1' })).toBeTruthy();
  });

  it('offers to register this year\'s edition when the code already exists', async () => {
    mocks.registerSubject.mockRejectedValue({ code: 'SUBJECT_ALREADY_EXISTS', params: { id: 5 } });
    mocks.registerEdition.mockResolvedValue({ ...created, subject: { ...created.subject, id: 5 } });
    renderPage();
    const dialog = await openForm();

    await fillSubject(dialog);
    fireEvent.click(within(dialog).getByRole('button', { name: 'Register subject' }));
    const instead = await within(dialog).findByRole('button', { name: 'Create the 2026/2027 edition' });
    fireEvent.click(instead);

    await waitFor(() => expect(mocks.registerEdition).toHaveBeenCalledWith(5, {
      academicYear: 2026, teacherNumbers: [100], classSections: [],
    }));
  });

  it('shows an error under Degrees when no degree is given', async () => {
    mocks.registerSubject.mockRejectedValue({
      code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'degrees', code: 'NOT_EMPTY' }],
    });
    renderPage();
    const dialog = await openForm();

    fireEvent.change(within(dialog).getByLabelText('Code'), { target: { value: 'MAT1' } });
    fireEvent.change(within(dialog).getByLabelText('Name'), { target: { value: 'Maths' } });
    await within(dialog).findByRole('option', { name: 'Programming' });
    fireEvent.change(within(dialog).getByLabelText('Category'), { target: { value: '1' } });
    // remove the only degree row
    fireEvent.click(within(dialog).getByRole('button', { name: 'Remove degree 1' }));
    fireEvent.click(within(dialog).getByRole('button', { name: 'Register subject' }));

    expect(await within(dialog).findByText(/must not be empty/i)).toBeTruthy();
  });

  it('shows an error under Teachers when no teacher is given', async () => {
    mocks.registerSubject.mockRejectedValue({ code: 'AT_LEAST_ONE_TEACHER_REQUIRED' });
    renderPage();
    const dialog = await openForm();

    fireEvent.change(within(dialog).getByLabelText('Code'), { target: { value: 'MAT1' } });
    fireEvent.change(within(dialog).getByLabelText('Name'), { target: { value: 'Maths' } });
    await within(dialog).findByRole('option', { name: 'Programming' });
    fireEvent.change(within(dialog).getByLabelText('Category'), { target: { value: '1' } });
    await within(dialog).findByRole('option', { name: 'Computer Science' });
    fireEvent.change(within(dialog).getByLabelText('Degree'), { target: { value: '1' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Register subject' }));

    expect(await within(dialog).findByText(/at least one teacher/i)).toBeTruthy();
  });
});
