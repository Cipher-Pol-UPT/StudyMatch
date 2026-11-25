import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getStudyRoom, getStudyRooms, joinStudyRoom, leaveStudyRoom } from '../src/api/studyRooms';
import i18n from '../src/i18n';
import { StudyRoomsPage } from '../src/pages/studyRooms/StudyRoomsPage';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { CurrentUser } from '../src/types/currentUser';
import type { StudyRoomData, StudyRoomDetailData } from '../src/types/studyRooms';

vi.mock('../src/api/studyRooms', () => ({
  getStudyRooms: vi.fn(),
  getStudyRoom: vi.fn(),
  joinStudyRoom: vi.fn(),
  leaveStudyRoom: vi.fn(),
  getMyTutorSubjects: vi.fn(() => Promise.resolve([])),
  createStudyRoom: vi.fn(),
}));

vi.mock('../src/api/subjects', () => ({
  getSubjects: vi.fn(() => Promise.resolve([])),
}));

const user: CurrentUser = { number: 45123, name: 'Test Student', institutionalEmail: '45123@students.studymatch.com', role: 'STUDENT', mustChangePassword: false, personalityTestRequired: false };
// the page's clock: the morning of the 5th, before the rooms start
const initialDate = new Date(2026, 9, 5, 9, 0);
const LINK = 'https://meet.google.com/abc-defg-hij';

const open = (changes: Partial<StudyRoomData> = {}): StudyRoomData => ({
  id: 1, subjectId: 1, subjectName: 'Test Quality', tutorNumber: 9001, tutorName: 'Test Tutor',
  startsAt: '2026-10-05T14:00:00', endsAt: '2026-10-05T15:30:00', description: 'Test regression tests',
  capacity: 8, link: null, status: 'SCHEDULED', participantCount: 3, availablePlaces: 5, ...changes,
});
const detail = (room: StudyRoomData): StudyRoomDetailData => ({
  ...room, tutorAverageRating: 4.6, tutorRatingCount: 12, hosted: false, joined: room.link != null, canJoin: room.link == null,
});

function renderPage() {
  render(<StudyMatchProvider><StudyRoomsPage currentUser={user} initialDate={initialDate} /></StudyMatchProvider>);
}

async function openRoom() {
  fireEvent.click(await screen.findByRole('button', { name: /^View Test regression tests/ }));
  return screen.findByRole('dialog', { name: 'Selected study room' });
}

beforeEach(async () => {
  await i18n.changeLanguage('en');
  vi.mocked(getStudyRooms).mockResolvedValue([open()]);
  vi.mocked(getStudyRoom).mockImplementation(async () => detail(open()));
});
afterEach(() => {
  cleanup();
  vi.mocked(joinStudyRoom).mockReset();
  vi.mocked(leaveStudyRoom).mockReset();
});

describe('join and leave a StudyRoom', () => {
  it('joins, says so in a toast, and the card switches to Joined with Leave', async () => {
    vi.mocked(joinStudyRoom).mockResolvedValue(open({ link: LINK, participantCount: 4, availablePlaces: 4 }));
    renderPage();
    const dialog = await openRoom();

    fireEvent.click(within(dialog).getByRole('button', { name: 'Join' }));

    expect(await screen.findByText('You joined Test regression tests. Your inscriptions have been updated.')).toBeTruthy();
    expect(within(dialog).getByText('Joined')).toBeTruthy();
    expect(within(dialog).getByRole('button', { name: 'Leave' })).toBeTruthy();
    expect(within(dialog).getByText('4/8 · 4 left')).toBeTruthy();
  });

  it('asks before leaving, and Stay keeps the place', async () => {
    vi.mocked(getStudyRooms).mockResolvedValue([open({ link: LINK })]);
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /^Joined/ }));
    const card = await screen.findByRole('article', { name: 'Test regression tests' });

    fireEvent.click(within(card).getByRole('button', { name: 'Leave' }));
    const confirm = screen.getByRole('dialog', { name: 'Leave this StudyRoom?' });
    expect(confirm.textContent).toContain('Your place goes back to the other students.');
    fireEvent.click(within(confirm).getByRole('button', { name: 'Stay' }));

    expect(leaveStudyRoom).not.toHaveBeenCalled();
    expect(screen.getByRole('article', { name: 'Test regression tests' })).toBeTruthy();
  });

  it('leaves once confirmed, and the room drops out of Joined', async () => {
    vi.mocked(getStudyRooms).mockResolvedValue([open({ link: LINK })]);
    vi.mocked(leaveStudyRoom).mockResolvedValue(open({ participantCount: 2, availablePlaces: 6 }));
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /^Joined/ }));
    fireEvent.click(within(await screen.findByRole('article', { name: 'Test regression tests' })).getByRole('button', { name: 'Leave' }));

    fireEvent.click(within(screen.getByRole('dialog', { name: 'Leave this StudyRoom?' })).getByRole('button', { name: 'Leave' }));

    expect(await screen.findByText('You left Test regression tests. Your inscriptions have been updated.')).toBeTruthy();
    expect(leaveStudyRoom).toHaveBeenCalledWith(1);
    await waitFor(() => expect(screen.queryByRole('article', { name: 'Test regression tests' })).toBeNull());
  });

  it('shows a full room as Full with a toast when it filled up meanwhile', async () => {
    vi.mocked(joinStudyRoom).mockRejectedValue({ code: 'STUDYROOM_FULL' });
    renderPage();
    const dialog = await openRoom();
    vi.mocked(getStudyRoom).mockImplementation(async () => detail(open({ status: 'FULL', participantCount: 8, availablePlaces: 0 })));

    fireEvent.click(within(dialog).getByRole('button', { name: 'Join' }));

    expect(await screen.findByText('This StudyRoom is full.')).toBeTruthy();
    const full = await within(dialog).findByRole('button', { name: 'Full' });
    expect((full as HTMLButtonElement).disabled).toBe(true);
  });

  it('names the room it overlaps in a banner above the card', async () => {
    vi.mocked(joinStudyRoom).mockRejectedValue({
      code: 'JOINED_STUDYROOM_OVERLAP', params: { id: 7, subject: 'Test Statistics', startsAt: '2026-10-05T14:30:00', endsAt: '2026-10-05T15:30:00' },
    });
    renderPage();
    const dialog = await openRoom();

    fireEvent.click(within(dialog).getByRole('button', { name: 'Join' }));

    const banner = await within(dialog).findByRole('alert');
    expect(banner.textContent).toContain('Test Statistics');
    expect(banner.textContent).toContain('14:30 – 15:30');
    expect(banner.textContent).not.toContain('2026-10-05T');
  });

  it('switches to Leave when you had already joined', async () => {
    vi.mocked(joinStudyRoom).mockRejectedValue({ code: 'ALREADY_JOINED_STUDYROOM' });
    renderPage();
    const dialog = await openRoom();
    vi.mocked(getStudyRoom).mockImplementation(async () => detail(open({ link: LINK })));

    fireEvent.click(within(dialog).getByRole('button', { name: 'Join' }));

    expect(await within(dialog).findByRole('button', { name: 'Leave' })).toBeTruthy();
  });

  it('offers neither Join nor Leave once the room has started', async () => {
    vi.mocked(getStudyRooms).mockResolvedValue([open({ startsAt: '2026-10-05T08:00:00', endsAt: '2026-10-05T10:00:00' })]);
    vi.mocked(getStudyRoom).mockImplementation(async () => detail(open({ startsAt: '2026-10-05T08:00:00', endsAt: '2026-10-05T10:00:00' })));
    renderPage();
    const dialog = await openRoom();

    await within(dialog).findByText('Test Tutor');
    expect(within(dialog).queryByRole('button', { name: 'Join' })).toBeNull();
    expect(within(dialog).queryByRole('button', { name: 'Leave' })).toBeNull();
  });
});
