import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { addDayOff, getAcademicYears, getDaysOff, removeDayOff } from '../src/api/academicYears';
import i18n from '../src/i18n';
import { StaffAcademicYearPage } from '../src/pages/staff/StaffAcademicYearPage';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { AcademicYearData, DayOffData } from '../src/types/academicYears';

vi.mock('../src/api/academicYears', () => ({
  getAcademicYears: vi.fn(),
  getDaysOff: vi.fn(),
  addDayOff: vi.fn(),
  removeDayOff: vi.fn(),
}));

const year: AcademicYearData = { startYear: 2096,
  semesters: { semester1Start: '2096-09-15', semester1End: '2097-01-31', semester2Start: '2097-02-15', semester2End: '2097-06-30' } };
const christmas: DayOffData = { id: 1, firstDay: '2096-12-21', lastDay: '2097-01-01', reason: 'Christmas break' };
const holiday: DayOffData = { id: 2, firstDay: '2096-12-08', lastDay: '2096-12-08', reason: 'Immaculate Conception' };

const dialog = () => screen.getByRole('dialog', { name: 'Mark days' });
// the days and the reason of each row of the list (the calendar has rows too), the row also has Remove. A range has
// thin spaces around its dash, read here as plain ones
const rows = () => within(screen.getByRole('table', { name: 'Days without classes' })).getAllByRole('row').slice(1)
  .map(row => [...row.querySelectorAll('td')].slice(0, 2).map(cell => cell.textContent?.replace(/\s+/g, ' ')));

function renderPage() {
  render(<StudyMatchProvider><StaffAcademicYearPage /></StudyMatchProvider>);
}

async function fill(firstDay: string, lastDay: string | null, reason: string) {
  // it waits for the list, so a day can't be added twice while it loads
  const mark = await screen.findByRole('button', { name: 'Mark days' });
  await waitFor(() => expect((mark as HTMLButtonElement).disabled).toBe(false));
  fireEvent.click(mark);
  fireEvent.change(within(dialog()).getByLabelText('First day'), { target: { value: firstDay } });
  if (lastDay) fireEvent.change(within(dialog()).getByLabelText(/Last day/), { target: { value: lastDay } });
  fireEvent.change(within(dialog()).getByLabelText('Reason'), { target: { value: reason } });
  fireEvent.click(within(dialog()).getByRole('button', { name: 'Mark without classes' }));
}

beforeEach(async () => {
  await i18n.changeLanguage('en');
  vi.mocked(getAcademicYears).mockResolvedValue([year, { startYear: 2095, semesters: null }]);
  vi.mocked(getDaysOff).mockResolvedValue([christmas]);
});
afterEach(() => {
  cleanup();
  vi.mocked(addDayOff).mockReset();
  vi.mocked(removeDayOff).mockReset();
});

describe('days without classes', () => {
  it("lists the current year's days with their reason, the earliest first, and the semesters above", async () => {
    vi.mocked(getDaysOff).mockResolvedValue([holiday, christmas]);
    renderPage();

    await screen.findByText('Christmas break');
    expect(rows()).toEqual([['December 8, 2096', 'Immaculate Conception'], ['December 21, 2096 – January 1, 2097', 'Christmas break']]);
    expect(getDaysOff).toHaveBeenCalledWith(2096);
    expect(screen.getByText('Semester 1: September 15, 2096 – January 31, 2097 · Semester 2: February 15 – June 30, 2097')).toBeTruthy();
  });

  it('says when there are no days yet', async () => {
    vi.mocked(getDaysOff).mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText('No days without classes yet')).toBeTruthy();
  });

  it('marks one day, without a last day, and lists it in order', async () => {
    vi.mocked(addDayOff).mockResolvedValue(holiday);
    renderPage();
    await fill('2096-12-08', null, '  Immaculate Conception ');

    await waitFor(() => expect(addDayOff).toHaveBeenCalledWith(2096, { firstDay: '2096-12-08', lastDay: null, reason: 'Immaculate Conception' }));
    expect(await screen.findByText('December 8, 2096 marked without classes.')).toBeTruthy();
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Mark days' })).toBeNull());
    expect(rows().map(([days]) => days)).toEqual(['December 8, 2096', 'December 21, 2096 – January 1, 2097']);
  });

  it('marks a range with a last day', async () => {
    vi.mocked(addDayOff).mockResolvedValue({ id: 3, firstDay: '2097-01-18', lastDay: '2097-01-29', reason: 'Exams' });
    renderPage();
    await fill('2097-01-18', '2097-01-29', 'Exams');

    await waitFor(() => expect(addDayOff).toHaveBeenCalledWith(2096, { firstDay: '2097-01-18', lastDay: '2097-01-29', reason: 'Exams' }));
    expect(await screen.findByText('January 18 – 29, 2097')).toBeTruthy();
  });

  it("stops a last day before the first one before sending", async () => {
    renderPage();
    await fill('2097-01-29', '2097-01-18', 'Exams');

    expect(within(dialog()).getByText('The last day has to be on or after the first day.')).toBeTruthy();
    expect(addDayOff).not.toHaveBeenCalled();
  });

  it("shows the day it falls on under the first day when it's already marked", async () => {
    vi.mocked(addDayOff).mockRejectedValue({ code: 'DAY_OFF_OVERLAPS', params: { firstDay: '2096-12-21', lastDay: '2097-01-01', reason: 'Christmas break' } });
    renderPage();
    await fill('2096-12-22', null, 'Exams');

    expect(await within(dialog()).findByText('It falls on Christmas break, from December 21, 2096 to January 1, 2097, which is already marked.')).toBeTruthy();
  });

  it('shows a day outside the semesters under the first day', async () => {
    vi.mocked(addDayOff).mockRejectedValue({ code: 'DAY_OFF_OUTSIDE_SEMESTERS' });
    renderPage();
    await fill('2097-02-05', null, 'Break');

    expect(await within(dialog()).findByText('Every day has to be inside the same semester.')).toBeTruthy();
  });

  it('opens the form on the day picked on the calendar', async () => {
    renderPage();
    await screen.findByText('Christmas break');
    // it opens on the first month of classes, since today is long before this year
    fireEvent.click(screen.getByRole('button', { name: /Friday, September 21, 2096/ }));

    expect((within(dialog()).getByLabelText('First day') as HTMLInputElement).value).toBe('2096-09-21');
  });

  it('removes a day and gives it its classes back', async () => {
    vi.mocked(removeDayOff).mockResolvedValue();
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /^Remove December 21, 2096\s–\sJanuary 1, 2097$/ }));

    expect(await screen.findByText('December 21, 2096 – January 1, 2097 has classes again.')).toBeTruthy();
    expect(removeDayOff).toHaveBeenCalledWith(2096, 1);
    expect(screen.queryByText('Christmas break')).toBeNull();
  });

  it("asks for the semester dates first when the year doesn't have them", async () => {
    vi.mocked(getAcademicYears).mockResolvedValue([{ startYear: 2096, semesters: null }]);
    renderPage();

    expect(await screen.findByText("Set this year's semester dates before marking days without classes.")).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Mark days' }) as HTMLButtonElement).disabled).toBe(true);
  });
});
