import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { confirmAttendance, getStudyRoomParticipants, getStudyRooms } from '../src/api/studyRooms';
import i18n from '../src/i18n';
import { StudyRoomsPage } from '../src/pages/studyRooms/StudyRoomsPage';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { StudyRoomData } from '../src/types/studyRooms';

vi.mock('../src/api/studyRooms', () => ({
  getMyTutorSubjects: vi.fn(() => Promise.resolve([])),
  createStudyRoom: vi.fn(),
  getStudyRooms: vi.fn(),
  getStudyRoom: vi.fn(() => Promise.resolve({})),
  joinStudyRoom: vi.fn(),
  leaveStudyRoom: vi.fn(),
  updateStudyRoom: vi.fn(),
  cancelStudyRoom: vi.fn(),
  getStudyRoomParticipants: vi.fn(),
  confirmAttendance: vi.fn(),
}));

vi.mock('../src/api/subjects', () => ({
  getSubjects: vi.fn(() => Promise.resolve([])),
}));

const tutor = { number: 45123, name: 'Test Tutor' };

// a local time like the backend's, some hours from now (negative for the past)
const inHours = (hours: number) => {
  const date = new Date(Date.now() + hours * 3_600_000);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`;
};

// ended an hour ago, not confirmed yet
const ended = (changes: Partial<StudyRoomData> = {}): StudyRoomData => ({
  id: 1, subjectId: 7, subjectName: 'Test Quality', tutorNumber: tutor.number, tutorName: tutor.name,
  startsAt: inHours(-2), endsAt: inHours(-1), description: 'Test regression tests', capacity: 8,
  link: 'https://meet.google.com/abc-defg-hij', status: 'SCHEDULED', participantCount: 3, availablePlaces: 5, ...changes,
});

const participants = [
  { number: 50001, name: 'Test Ana', attended: false },
  { number: 50002, name: 'Test Rui', attended: false },
  { number: 50003, name: 'Test Marta', attended: false },
];

async function openHosting() {
  render(<StudyMatchProvider><StudyRoomsPage currentUser={tutor} /></StudyMatchProvider>);
  fireEvent.click(await screen.findByRole('button', { name: /^Hosting/ }));
  await screen.findByRole('article', { name: 'Test regression tests' });
}

async function openDialog() {
  await openHosting();
  fireEvent.click(screen.getByRole('button', { name: 'Confirm attendance' }));
  return screen.findByRole('dialog', { name: 'Confirm attendance' });
}

const card = () => screen.getByRole('article', { name: 'Test regression tests' });

beforeEach(async () => {
  await i18n.changeLanguage('en');
  vi.mocked(getStudyRooms).mockResolvedValue([ended()]);
  vi.mocked(getStudyRoomParticipants).mockResolvedValue(participants);
});
afterEach(() => {
  cleanup();
  vi.mocked(confirmAttendance).mockReset();
});

describe('confirm StudyRoom attendance', () => {
  it('shows the banner only for your rooms that ended and are not Completed', async () => {
    vi.mocked(getStudyRooms).mockResolvedValue([
      ended(),
      ended({ id: 2, description: 'Test upcoming', startsAt: inHours(24), endsAt: inHours(25) }),
      ended({ id: 3, description: 'Test done', status: 'COMPLETED' }),
      ended({ id: 4, description: 'Test not mine', tutorNumber: 99 }),
    ]);
    await openHosting();

    expect(screen.getByText('Test regression tests has ended')).toBeTruthy();
    expect(screen.getAllByRole('button', { name: 'Confirm attendance' })).toHaveLength(1);
  });

  it('confirms the ticked participants, then the room is Completed with how many came and no banner', async () => {
    vi.mocked(confirmAttendance).mockResolvedValue(ended({ status: 'COMPLETED' }));
    const dialog = await openDialog();

    expect(within(dialog).getByText(/Test regression tests ·/)).toBeTruthy();
    fireEvent.click(within(dialog).getByRole('checkbox', { name: 'Test Ana' }));
    fireEvent.click(within(dialog).getByRole('checkbox', { name: 'Test Marta' }));
    fireEvent.click(within(dialog).getByRole('button', { name: 'Confirm 2 attendees' }));

    expect(await screen.findByText('Attendance confirmed. The StudyRoom is completed.')).toBeTruthy();
    expect(confirmAttendance).toHaveBeenCalledWith(1, [50001, 50003]);
    expect(within(card()).getByText('Completed')).toBeTruthy();
    expect(within(card()).getByText('2 attended')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Confirm attendance' })).toBeNull();
  });

  it('confirms with nobody ticked', async () => {
    vi.mocked(confirmAttendance).mockResolvedValue(ended({ status: 'COMPLETED' }));
    const dialog = await openDialog();

    fireEvent.click(within(dialog).getByRole('button', { name: 'Confirm nobody came' }));

    await waitFor(() => expect(confirmAttendance).toHaveBeenCalledWith(1, []));
    expect(await within(card()).findByText('Nobody attended')).toBeTruthy();
  });

  it('shows a toast when the room has not ended yet', async () => {
    vi.mocked(confirmAttendance).mockRejectedValue({ code: 'STUDYROOM_NOT_ENDED' });
    const dialog = await openDialog();

    fireEvent.click(within(dialog).getByRole('button', { name: 'Confirm nobody came' }));

    expect(await screen.findByText('The StudyRoom has not ended yet.')).toBeTruthy();
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Confirm attendance' })).toBeNull());
    expect(within(card()).queryByText('Completed')).toBeNull();
  });

  it('shows a toast when it is already closed, and the room as Completed with who the backend has', async () => {
    vi.mocked(confirmAttendance).mockRejectedValue({ code: 'STUDYROOM_CLOSED' });
    const dialog = await openDialog();
    vi.mocked(getStudyRoomParticipants).mockResolvedValue(participants.map((person, index) => ({ ...person, attended: index === 0 })));

    fireEvent.click(within(dialog).getByRole('button', { name: 'Confirm nobody came' }));

    expect(await screen.findByText('This StudyRoom is closed.')).toBeTruthy();
    expect(within(card()).getByText('Completed')).toBeTruthy();
    expect(await within(card()).findByText('1 attended')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Confirm attendance' })).toBeNull();
  });

  it('says nobody joined when the list is empty', async () => {
    vi.mocked(getStudyRoomParticipants).mockResolvedValue([]);
    const dialog = await openDialog();

    expect(within(dialog).getByText('Nobody joined this StudyRoom.')).toBeTruthy();
    expect(within(dialog).getByRole('button', { name: 'Confirm nobody came' })).toBeTruthy();
  });
});
