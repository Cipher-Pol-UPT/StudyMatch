import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { applyForTutoring, getApplicableSubjects } from '../src/api/studyRooms';
import i18n from '../src/i18n';
import { StudyRoomsPage } from '../src/pages/studyRooms/StudyRoomsPage';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { TutorEligibleSubjectData } from '../src/types/studyRooms';

vi.mock('../src/api/studyRooms', () => ({
  getMyTutorSubjects: vi.fn(() => Promise.resolve([])),
  getStudyRooms: vi.fn(() => Promise.resolve([])),
  getStudyRoom: vi.fn(() => Promise.resolve({})),
  getApplicableSubjects: vi.fn(),
  applyForTutoring: vi.fn(),
}));

vi.mock('../src/api/subjects', () => ({
  getSubjects: vi.fn(() => Promise.resolve([])),
}));

const student = { number: 50001, name: 'Test Ana' };
const subject = (changes: Partial<TutorEligibleSubjectData>): TutorEligibleSubjectData => ({
  subjectId: 1, subjectName: 'Test Programming', finalGrade: 19, position: 2, editionSize: 120,
  eligible: true, hasPendingApplication: false, hasTutorRole: false, hasCurrentEdition: true, ...changes,
});
// one of each state; a rejected application leaves nothing pending, so Test Databases can be applied for again
const subjects = [
  subject({}),
  subject({ subjectId: 2, subjectName: 'Test Algebra', hasTutorRole: true }),
  subject({ subjectId: 3, subjectName: 'Test Physics', hasPendingApplication: true }),
  subject({ subjectId: 4, subjectName: 'Test History', hasCurrentEdition: false }),
  subject({ subjectId: 5, subjectName: 'Test Calculus', finalGrade: 12, position: 40, eligible: false }),
];

async function openBecomeTutor() {
  render(<StudyMatchProvider><StudyRoomsPage currentUser={student} /></StudyMatchProvider>);
  fireEvent.click(await screen.findByRole('button', { name: 'Become a tutor' }));
  await screen.findByRole('table');
}
const row = (name: string) => screen.getByRole('cell', { name }).closest('tr') as HTMLTableRowElement;

beforeEach(async () => {
  await i18n.changeLanguage('en');
  vi.mocked(getApplicableSubjects).mockResolvedValue(subjects);
});
afterEach(() => { cleanup(); vi.clearAllMocks(); });

describe('apply for tutoring', () => {
  it('shows each passed subject with its rank and state, and Apply only where you can', async () => {
    await openBecomeTutor();

    expect(within(row('Test Programming')).getByText('2 of 120')).toBeTruthy();
    expect(within(row('Test Programming')).getByText('Can apply')).toBeTruthy();
    expect(within(row('Test Algebra')).getByText('Tutor')).toBeTruthy();
    expect(within(row('Test Physics')).getByText('Pending')).toBeTruthy();
    expect(within(row('Test History')).getByText('Not taught this year')).toBeTruthy();
    expect(within(row('Test Calculus')).getByText('Not in the top 5%')).toBeTruthy();
    expect(screen.getAllByRole('button', { name: /^Apply to tutor/ }).map(button => button.getAttribute('aria-label'))).toEqual(['Apply to tutor Test Programming']);
  });

  it('applies with a motivation, and the row turns Pending', async () => {
    vi.mocked(applyForTutoring).mockResolvedValue({ id: 9, subjectId: 1, subjectName: 'Test Programming', status: 'PENDING', submittedAt: '2026-11-27T10:00:00' });
    await openBecomeTutor();

    fireEvent.click(screen.getByRole('button', { name: 'Apply to tutor Test Programming' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Tutor Test Programming' }));
    expect(dialog.getByText('Final grade 19, 2 of 120 in your class.')).toBeTruthy();
    fireEvent.change(dialog.getByLabelText(/Motivation/), { target: { value: '  I like helping  ' } });
    fireEvent.click(dialog.getByRole('button', { name: 'Apply' }));

    expect(await screen.findByText('You applied to tutor Test Programming.')).toBeTruthy();
    expect(applyForTutoring).toHaveBeenCalledWith(1, 'I like helping');
    expect(within(row('Test Programming')).getByText('Pending')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Apply to tutor Test Programming' })).toBeNull();
  });

  it('sends no motivation when it is left empty', async () => {
    vi.mocked(applyForTutoring).mockResolvedValue({ id: 9, subjectId: 1, subjectName: 'Test Programming', status: 'PENDING', submittedAt: '2026-11-27T10:00:00' });
    await openBecomeTutor();

    fireEvent.click(screen.getByRole('button', { name: 'Apply to tutor Test Programming' }));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Apply' }));

    await waitFor(() => expect(applyForTutoring).toHaveBeenCalledWith(1, null));
  });

  it.each([
    ['TUTOR_APPLICATION_ALREADY_PENDING', 'You already have a Pending application for this Subject.', 'Pending'],
    ['TUTOR_ROLE_ALREADY_EXISTS', 'You are already a tutor of this Subject.', 'Tutor'],
    ['NOT_ELIGIBLE_FOR_TUTORING', 'Only Students whose final grade is in the top 5% of their edition can tutor this Subject.', 'Not in the top 5%'],
    ['SUBJECT_NOT_TAUGHT_THIS_YEAR', "This Subject isn't taught this academic year.", 'Not taught this year'],
  ])('%s shows a toast and the row updates', async (code, message, state) => {
    vi.mocked(applyForTutoring).mockRejectedValue({ code });
    await openBecomeTutor();

    fireEvent.click(screen.getByRole('button', { name: 'Apply to tutor Test Programming' }));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Apply' }));

    expect(await screen.findByText(message)).toBeTruthy();
    expect(within(row('Test Programming')).getByText(state)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Apply to tutor Test Programming' })).toBeNull();
  });

  it('says so when no subject is passed yet', async () => {
    vi.mocked(getApplicableSubjects).mockResolvedValue([]);
    render(<StudyMatchProvider><StudyRoomsPage currentUser={student} /></StudyMatchProvider>);
    fireEvent.click(await screen.findByRole('button', { name: 'Become a tutor' }));

    expect(await screen.findByText('No subjects passed yet')).toBeTruthy();
  });
});
