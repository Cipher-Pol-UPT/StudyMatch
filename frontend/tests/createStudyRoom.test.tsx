import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createStudyRoom, getMyTutorSubjects } from '../src/api/studyRooms';
import i18n from '../src/i18n';
import { StudyRoomsPage } from '../src/pages/studyRooms/StudyRoomsPage';
import { StudyMatchProvider } from '../src/studymatch-ui';

vi.mock('../src/api/studyRooms', () => ({
  getMyTutorSubjects: vi.fn(),
  createStudyRoom: vi.fn(),
  getStudyRooms: vi.fn(() => Promise.resolve([])),
  getStudyRoom: vi.fn(() => Promise.resolve({})),
  joinStudyRoom: vi.fn(),
  leaveStudyRoom: vi.fn(),
}));

vi.mock('../src/api/subjects', () => ({
  getSubjects: vi.fn(() => Promise.resolve([])),
}));

const user = { number: 45123, name: 'Test Tutor' };
const initialDate = new Date(2026, 9, 19);
const subjects = [{ subjectId: 7, subjectName: 'Test Maths' }, { subjectId: 9, subjectName: 'Test Physics' }];
const form = () => screen.getByRole('dialog', { name: 'New StudyRoom' });

function renderPage() {
  render(<StudyMatchProvider><StudyRoomsPage currentUser={user} initialDate={initialDate} /></StudyMatchProvider>);
}

async function openForm() {
  fireEvent.click(await screen.findByRole('button', { name: 'New StudyRoom' }));
  return form();
}

function chooseOwnLink(dialog: HTMLElement) {
  fireEvent.click(within(dialog).getByRole('radio', { name: /Use my own link/ }));
}

function fillLink(dialog: HTMLElement, link: string) {
  chooseOwnLink(dialog);
  fireEvent.change(within(dialog).getByLabelText('Google Meet link'), { target: { value: link } });
}

afterEach(() => {
  cleanup();
  vi.mocked(createStudyRoom).mockReset();
});
beforeEach(async () => {
  await i18n.changeLanguage('en');
  vi.mocked(getMyTutorSubjects).mockResolvedValue(subjects);
});

describe('create study room', () => {
  it('hides New StudyRoom for students who do not tutor anything', async () => {
    vi.mocked(getMyTutorSubjects).mockResolvedValue([]);
    renderPage();
    await waitFor(() => expect(getMyTutorSubjects).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: 'New StudyRoom' })).toBeNull();
  });

  it('sends the subject id, times, places and link, then shows the room on the calendar', async () => {
    vi.mocked(createStudyRoom).mockResolvedValue({
      id: 1, subjectId: 9, subjectName: 'Test Physics', tutorNumber: 45123, tutorName: 'Test Tutor',
      startsAt: '2026-10-20T18:00:00', endsAt: '2026-10-20T19:00:00', description: 'Test session', capacity: 8,
      link: 'https://meet.google.com/abc-defg-hij', status: 'SCHEDULED', participantCount: 0,
    });
    renderPage();
    const dialog = await openForm();

    fireEvent.change(within(dialog).getByLabelText('Subject'), { target: { value: '9' } });
    fillLink(dialog, ' https://meet.google.com/abc-defg-hij ');
    fireEvent.change(within(dialog).getByLabelText(/Description/), { target: { value: 'Test session' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create StudyRoom' }));

    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'New StudyRoom' })).toBeNull(), { timeout: 3000 });
    expect(createStudyRoom).toHaveBeenCalledWith({
      subjectId: 9, startsAt: '2026-10-20T18:00', endsAt: '2026-10-20T19:00', capacity: 8,
      link: 'https://meet.google.com/abc-defg-hij', description: 'Test session',
    });
    expect(screen.getByRole('button', { name: /^View Test session,/ })).toBeTruthy();
    expect(await screen.findByText('StudyRoom created.')).toBeTruthy();
  });

  it('keeps the form open with the backend error when the room is refused', async () => {
    vi.mocked(createStudyRoom).mockRejectedValue({
      code: 'STUDYROOM_OVERLAP', params: { subject: 'Test Maths', startsAt: '18:00', endsAt: '19:00' },
    });
    renderPage();
    const dialog = await openForm();

    fillLink(dialog, 'https://meet.google.com/abc-defg-hij');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create StudyRoom' }));

    expect(await within(dialog).findByText('This overlaps your Test Maths StudyRoom (18:00 – 19:00).')).toBeTruthy();
    expect(form()).toBeTruthy();
  });

  it('shows a field error from the backend under its field', async () => {
    vi.mocked(createStudyRoom).mockRejectedValue({
      code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'link', code: 'NOT_BLANK' }],
    });
    renderPage();
    const dialog = await openForm();

    chooseOwnLink(dialog);
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create StudyRoom' }));

    const link = within(dialog).getByLabelText('Google Meet link');
    await waitFor(() => expect(link.getAttribute('aria-invalid')).toBe('true'));
  });

  // generate a Google Meet link (#269)

  it('asks for the Meet link to be created by default, sending no link', async () => {
    vi.mocked(createStudyRoom).mockResolvedValue({
      id: 2, subjectId: 7, subjectName: 'Test Maths', tutorNumber: 45123, tutorName: 'Test Tutor',
      startsAt: '2026-10-20T18:00:00', endsAt: '2026-10-20T19:00:00', description: null, capacity: 8,
      link: 'https://meet.google.com/xyz-wxyz-xyz', status: 'SCHEDULED', participantCount: 0,
    });
    renderPage();
    const dialog = await openForm();

    expect(within(dialog).getByRole('radio', { name: /Create a Meet link for me/ })).toHaveProperty('checked', true);
    expect(within(dialog).queryByLabelText('Google Meet link')).toBeNull();
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create StudyRoom' }));

    await waitFor(() => expect(createStudyRoom).toHaveBeenCalledWith(expect.objectContaining({ subjectId: 7, link: null })));
    expect(await screen.findByText('StudyRoom created.')).toBeTruthy();
  });

  it('switches to its own link with the error when Google can not create one', async () => {
    vi.mocked(createStudyRoom).mockRejectedValue({ code: 'MEET_LINK_UNAVAILABLE' });
    renderPage();
    const dialog = await openForm();

    fireEvent.click(within(dialog).getByRole('button', { name: 'Create StudyRoom' }));

    expect(await within(dialog).findByText("The Google Meet link couldn't be created right now. Paste your own link to create the StudyRoom.")).toBeTruthy();
    expect(within(dialog).getByRole('radio', { name: /Use my own link/ })).toHaveProperty('checked', true);
    expect(within(dialog).getByLabelText('Google Meet link')).toBeTruthy();
  });
});
