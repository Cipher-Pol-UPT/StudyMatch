import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { editSemesters, getAcademicYears, getDaysOff } from '../src/api/academicYears';
import i18n from '../src/i18n';
import { StaffAcademicYearPage } from '../src/pages/staff/StaffAcademicYearPage';
import { suggestedSemesters } from '../src/pages/staff/semesterDates';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { AcademicYearData } from '../src/types/academicYears';

vi.mock('../src/api/academicYears', () => ({
  getAcademicYears: vi.fn(),
  getDaysOff: vi.fn(),
  addDayOff: vi.fn(),
  removeDayOff: vi.fn(),
  editSemesters: vi.fn(),
}));

const year: AcademicYearData = { startYear: 2096,
  semesters: { semester1Start: '2096-09-15', semester1End: '2097-01-31', semester2Start: '2097-02-15', semester2End: '2097-06-30' } };

const dialog = () => screen.getByRole('dialog');
const field = (label: string) => within(dialog()).getByLabelText(label) as HTMLInputElement;
const type = (label: string, value: string) => fireEvent.change(field(label), { target: { value } });
const save = () => within(dialog()).getByRole('button', { name: 'Save dates' });

function renderPage() {
  render(<StudyMatchProvider><StaffAcademicYearPage /></StudyMatchProvider>);
}

async function openEdit() {
  fireEvent.click(await screen.findByRole('button', { name: 'Edit dates' }));
}

beforeEach(async () => {
  await i18n.changeLanguage('en');
  vi.mocked(getAcademicYears).mockResolvedValue([year]);
  vi.mocked(getDaysOff).mockResolvedValue([]);
});
afterEach(() => {
  cleanup();
  vi.mocked(editSemesters).mockReset();
});

describe('semester dates', () => {
  it("edits the current year's dates from the ones it has, and shows the new ones", async () => {
    vi.mocked(editSemesters).mockResolvedValue({ ...year, semesters: { ...year.semesters!, semester1Start: '2096-09-21' } });
    renderPage();
    await openEdit();
    expect(field('Semester 1 starts').value).toBe('2096-09-15');
    expect(field('Semester 2 ends').value).toBe('2097-06-30');

    type('Semester 1 starts', '2096-09-21');
    fireEvent.click(save());

    await waitFor(() => expect(editSemesters).toHaveBeenCalledWith(2096, { ...year.semesters, semester1Start: '2096-09-21' }));
    expect(await screen.findByText('Semester dates saved.')).toBeTruthy();
    expect(screen.getByText(/^September 21, 2096\s–\sJanuary 31, 2097$/)).toBeTruthy();
  });

  it("catches dates out of order under the date, before sending", async () => {
    renderPage();
    await openEdit();

    type('Semester 1 ends', '2096-09-10');
    expect(within(dialog()).getByText('Semester 1 has to end after it starts.')).toBeTruthy();
    expect(field('Semester 1 ends').getAttribute('aria-invalid')).toBe('true');
    expect(save().hasAttribute('disabled')).toBe(true);

    type('Semester 1 ends', '2097-01-31');
    type('Semester 2 starts', '2097-01-20');
    expect(within(dialog()).getByText('Semester 2 has to start after semester 1 ends.')).toBeTruthy();
    expect(field('Semester 2 starts').getAttribute('aria-invalid')).toBe('true');

    type('Semester 2 starts', '2097-02-15');
    type('Semester 2 ends', '2097-09-02');
    expect(within(dialog()).getByText('Every date has to be between September 1, 2096 and August 31, 2097.')).toBeTruthy();
    expect(field('Semester 2 ends').getAttribute('aria-invalid')).toBe('true');
    expect(editSemesters).not.toHaveBeenCalled();
  });

  it("puts the backend's code under the date it's about", async () => {
    vi.mocked(editSemesters).mockRejectedValue({ code: 'SEMESTER_ENDS_BEFORE_START', params: { semester: 2 } });
    renderPage();
    await openEdit();
    fireEvent.click(save());

    expect(await within(dialog()).findByText('Semester 2 has to end after it starts.')).toBeTruthy();
    expect(field('Semester 2 ends').getAttribute('aria-invalid')).toBe('true');
  });

  it('a year without dates asks for them, starting from mid-September to January and February to June', async () => {
    vi.mocked(getAcademicYears).mockResolvedValue([{ startYear: 2096, semesters: null }]);
    vi.mocked(editSemesters).mockResolvedValue(year);
    renderPage();

    expect(await screen.findByText('This year has no semester dates yet. Set them to mark days without classes and publish schedules.')).toBeTruthy();
    fireEvent.click(screen.getAllByRole('button', { name: 'Set the semester dates' })[0]);
    expect(field('Semester 1 starts').value).toBe('2096-09-15');
    expect(field('Semester 1 ends').value).toBe('2097-01-31');
    expect(field('Semester 2 starts').value).toBe('2097-02-15');
    expect(field('Semester 2 ends').value).toBe('2097-06-30');
    fireEvent.click(save());

    await waitFor(() => expect(editSemesters).toHaveBeenCalledWith(2096, suggestedSemesters(2096)));
    // the days without classes can be marked now
    expect(await screen.findByRole('button', { name: 'Mark days' })).toBeTruthy();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Mark days' }).hasAttribute('disabled')).toBe(false));
  });
});
