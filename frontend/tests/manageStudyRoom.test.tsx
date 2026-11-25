import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cancelStudyRoom, getStudyRooms, updateStudyRoom } from '../src/api/studyRooms';
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

// a local time like the backend's, some days from now
const inDays = (days: number, hour: number) => {
  const date = new Date();
  date.setDate(date.getDate() + days);
  date.setHours(hour, 0, 0, 0);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(hour)}:00:00`;
};

const room = (changes: Partial<StudyRoomData> = {}): StudyRoomData => ({
  id: 1, subjectId: 7, subjectName: 'Test Quality', tutorNumber: tutor.number, tutorName: tutor.name,
  startsAt: inDays(3, 14), endsAt: inDays(3, 15), description: 'Test regression tests', capacity: 8,
  link: 'https://meet.google.com/abc-defg-hij', status: 'SCHEDULED', participantCount: 5, availablePlaces: 3, ...changes,
});

async function openHosting() {
  render(<StudyMatchProvider><StudyRoomsPage currentUser={tutor} /></StudyMatchProvider>);
  fireEvent.click(await screen.findByRole('button', { name: /^Hosting/ }));
  return screen.findByRole('article', { name: 'Test regression tests' });
}

async function openEdit() {
  await openHosting();
  fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
  return screen.getByRole('dialog', { name: 'Edit StudyRoom' });
}

const save = (dialog: HTMLElement) => fireEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));

beforeEach(async () => {
  await i18n.changeLanguage('en');
  vi.mocked(getStudyRooms).mockResolvedValue([room()]);
});
afterEach(() => {
  cleanup();
  vi.mocked(updateStudyRoom).mockReset();
  vi.mocked(cancelStudyRoom).mockReset();
});

describe('manage my StudyRoom', () => {
  it('offers Edit and Cancel StudyRoom on a room you host that has not started', async () => {
    await openHosting();

    expect(screen.getByRole('button', { name: 'Edit' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Cancel StudyRoom' })).toBeTruthy();
  });

  it('offers neither once the room has started', async () => {
    vi.mocked(getStudyRooms).mockResolvedValue([room({ startsAt: inDays(-1, 14), endsAt: inDays(5, 15) })]);
    await openHosting();

    expect(screen.queryByRole('button', { name: 'Edit' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Cancel StudyRoom' })).toBeNull();
  });

  it('edits the room with its values filled in and the subject locked, then shows the new places', async () => {
    vi.mocked(updateStudyRoom).mockResolvedValue(room({ capacity: 10, availablePlaces: 5 }));
    const dialog = await openEdit();

    expect((within(dialog).getByLabelText('Subject') as HTMLSelectElement).disabled).toBe(true);
    expect((within(dialog).getByLabelText('Start') as HTMLInputElement).value).toBe('14:00');
    expect((within(dialog).getByLabelText(/Description/) as HTMLTextAreaElement).value).toBe('Test regression tests');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Increase' }));
    fireEvent.click(within(dialog).getByRole('button', { name: 'Increase' }));
    save(dialog);

    expect(await screen.findByText('StudyRoom updated.')).toBeTruthy();
    expect(updateStudyRoom).toHaveBeenCalledWith(1, {
      startsAt: room().startsAt.slice(0, 16), endsAt: room().endsAt.slice(0, 16), capacity: 10,
      link: 'https://meet.google.com/abc-defg-hij', description: 'Test regression tests',
    });
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Edit StudyRoom' })).toBeNull());
    expect(screen.getByText(/5\/10/)).toBeTruthy();
  });

  it('shows a capacity below the participants under Places', async () => {
    vi.mocked(updateStudyRoom).mockRejectedValue({ code: 'CAPACITY_BELOW_PARTICIPANTS', params: { participants: 5 } });
    const dialog = await openEdit();

    save(dialog);

    expect(await within(dialog).findByText('Capacity cannot be below 5 participants.')).toBeTruthy();
    expect(within(dialog).getByRole('spinbutton').getAttribute('aria-invalid')).toBe('true');
  });

  it('shows a start in the past under the date and an end before the start under the end time', async () => {
    vi.mocked(updateStudyRoom).mockRejectedValueOnce({ code: 'STUDYROOM_IN_PAST' });
    const dialog = await openEdit();
    save(dialog);
    expect(await within(dialog).findByText('The StudyRoom cannot start in the past.')).toBeTruthy();
    expect(within(dialog).getByLabelText('Date').getAttribute('aria-invalid')).toBe('true');

    vi.mocked(updateStudyRoom).mockRejectedValueOnce({ code: 'STUDYROOM_ENDS_BEFORE_START' });
    save(dialog);
    expect(await within(dialog).findByText('The StudyRoom must end after it starts.')).toBeTruthy();
    expect(within(dialog).getByLabelText('End').getAttribute('aria-invalid')).toBe('true');
  });

  it('shows an overlap in the banner at the top, naming the other room', async () => {
    vi.mocked(updateStudyRoom).mockRejectedValue({
      code: 'STUDYROOM_OVERLAP', params: { subject: 'Test Maths', startsAt: '14:00', endsAt: '15:00' },
    });
    const dialog = await openEdit();

    save(dialog);

    expect((await within(dialog).findByRole('alert')).textContent).toContain('This overlaps your Test Maths StudyRoom');
  });

  it('closes the form with a toast and takes the actions away when the room already started', async () => {
    vi.mocked(updateStudyRoom).mockRejectedValue({ code: 'STUDYROOM_ALREADY_STARTED' });
    const dialog = await openEdit();

    save(dialog);

    expect(await screen.findByText('This StudyRoom has already started.')).toBeTruthy();
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Edit StudyRoom' })).toBeNull());
    expect(screen.queryByRole('button', { name: 'Edit' })).toBeNull();
  });

  it('cancels with the reason, and the room leaves the list', async () => {
    vi.mocked(cancelStudyRoom).mockResolvedValue(room({ status: 'CANCELLED' }));
    await openHosting();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel StudyRoom' }));
    const dialog = screen.getByRole('dialog', { name: 'Cancel this StudyRoom?' });
    expect(dialog.textContent).toContain('The 5 participants are told');

    fireEvent.change(within(dialog).getByLabelText(/Reason/), { target: { value: ' I am ill ' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancel StudyRoom' }));

    expect(await screen.findByText('StudyRoom cancelled.')).toBeTruthy();
    expect(cancelStudyRoom).toHaveBeenCalledWith(1, 'I am ill');
    expect(screen.queryByRole('article', { name: 'Test regression tests' })).toBeNull();
  });

  it('cancels without a reason, sending none', async () => {
    vi.mocked(cancelStudyRoom).mockResolvedValue(room({ status: 'CANCELLED' }));
    await openHosting();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel StudyRoom' }));

    fireEvent.click(within(screen.getByRole('dialog', { name: 'Cancel this StudyRoom?' })).getByRole('button', { name: 'Cancel StudyRoom' }));

    await waitFor(() => expect(cancelStudyRoom).toHaveBeenCalledWith(1, null));
  });

  it('keeps the room with Keep it', async () => {
    await openHosting();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel StudyRoom' }));

    fireEvent.click(screen.getByRole('button', { name: 'Keep it' }));

    expect(cancelStudyRoom).not.toHaveBeenCalled();
    expect(screen.getByRole('article', { name: 'Test regression tests' })).toBeTruthy();
  });
});
