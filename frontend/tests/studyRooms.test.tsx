import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { StudyRoomsPage } from '../src/pages/studyRooms/StudyRoomsPage';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { CurrentUser } from '../src/types/currentUser';
import type { StudyRoomData, StudyRoomDetailData } from '../src/types/studyRooms';

const mocks = vi.hoisted(() => {
  const studentNumber = 45123;
  const rooms: StudyRoomData[] = [
    { id: 1, subjectId: 1, subjectName: 'Programming Lab', tutorNumber: 9001, tutorName: 'Tiago Costa',
      startsAt: '2026-10-05T17:30:00', endsAt: '2026-10-05T18:30:00', description: 'Clean code, clear thinking',
      capacity: 12, link: null, status: 'SCHEDULED', participantCount: 5, availablePlaces: 7 },
    { id: 2, subjectId: 2, subjectName: 'Statistics', tutorNumber: 9002, tutorName: 'Leonor Faria',
      startsAt: '2026-10-06T17:30:00', endsAt: '2026-10-06T18:30:00', description: 'Making sense of probability',
      capacity: 12, link: 'https://meet.google.com/abc-defg-hij', status: 'SCHEDULED', participantCount: 6, availablePlaces: 6 },
    { id: 3, subjectId: 1, subjectName: 'Programming Lab', tutorNumber: studentNumber, tutorName: 'Manu Yepes',
      startsAt: '2026-10-07T17:30:00', endsAt: '2026-10-07T18:30:00', description: 'Java collections in practice',
      capacity: 12, link: 'https://meet.google.com/xyz-wxyz-xyz', status: 'SCHEDULED', participantCount: 0, availablePlaces: 12 },
  ];
  const subjects = [{ id: 1, name: 'Programming Lab' }, { id: 2, name: 'Statistics' }];
  const detailOf = (id: number): StudyRoomDetailData => {
    const room = rooms.find((item) => item.id === id)!;
    return { ...room, tutorAverageRating: 4.9, tutorRatingCount: 18,
      hosted: room.tutorNumber === studentNumber,
      joined: room.tutorNumber !== studentNumber && room.link != null,
      canJoin: room.tutorNumber !== studentNumber && room.link == null };
  };
  const joinStudyRoom = vi.fn((id: number) => Promise.resolve({ ...rooms.find((item) => item.id === id)!,
    link: 'https://meet.google.com/abc-defg-hij', participantCount: 6, availablePlaces: 6 }));
  const leaveStudyRoom = vi.fn((id: number) => Promise.resolve({ ...rooms.find((item) => item.id === id)!,
    link: null, participantCount: 5, availablePlaces: 7 }));
  return { rooms, subjects, detailOf, joinStudyRoom, leaveStudyRoom };
});

vi.mock('../src/api/studyRooms', () => ({
  getStudyRooms: vi.fn(() => Promise.resolve(mocks.rooms)),
  getStudyRoom: vi.fn((id: number) => Promise.resolve(mocks.detailOf(id))),
  joinStudyRoom: mocks.joinStudyRoom,
  leaveStudyRoom: mocks.leaveStudyRoom,
  getMyTutorSubjects: vi.fn(() => Promise.resolve([])),
  createStudyRoom: vi.fn(),
}));

vi.mock('../src/api/subjects', () => ({
  getSubjects: vi.fn(() => Promise.resolve(mocks.subjects)),
}));

import { joinStudyRoom, leaveStudyRoom } from '../src/api/studyRooms';

const user: CurrentUser = { number: 45123, name: 'Manu Yepes', institutionalEmail: '45123@students.studymatch.com', role: 'STUDENT', mustChangePassword: false, personalityTestRequired: false };
const initialDate = new Date(2026, 9, 5);

function renderPage() {
  render(<StudyMatchProvider><StudyRoomsPage currentUser={user} initialDate={initialDate} /></StudyMatchProvider>);
}

afterEach(cleanup);
beforeEach(async () => { await i18n.changeLanguage('en'); });

describe('study rooms', () => {
  it('loads the rooms and the subjects from the backend', async () => {
    renderPage();
    expect(await screen.findByRole('button', { name: /^View Clean code/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Programming Lab' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Statistics' })).toBeTruthy();
    expect(screen.queryByText('Demo')).toBeNull();
  });

  it('shows only the clicked subject, then restores all subjects', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Statistics' }));
    expect(screen.getByRole('button', { name: /^View Making sense/ })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^View Clean code/ })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Show all subjects' }));
    expect(await screen.findByRole('button', { name: /^View Clean code/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: /^View Java collections/ })).toBeTruthy();
  });

  it('opens a room and shows its tutor and rating from the detail endpoint', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /^View Clean code/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Selected study room' });
    expect(within(dialog).getByRole('heading', { name: 'Clean code, clear thinking' })).toBeTruthy();
    expect(within(dialog).getByText('Tiago Costa')).toBeTruthy();
    expect(within(dialog).getByText('4.9')).toBeTruthy();
    expect(within(dialog).getByText('5/12 · 7 left')).toBeTruthy();
  });

  it('joining calls the backend and leaves the room updated', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /^View Clean code/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Selected study room' });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Join' }));
    await waitFor(() => expect(joinStudyRoom).toHaveBeenCalledWith(1));
    expect(await within(dialog).findByText('6/12 · 6 left')).toBeTruthy();
  });

  it('shows joined rooms in inscriptions and hosted rooms in my study rooms', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /^Joined/ }));
    expect(await screen.findByRole('article', { name: 'Making sense of probability' })).toBeTruthy();
    expect(screen.queryByRole('article', { name: 'Clean code, clear thinking' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /^Hosting/ }));
    expect(await screen.findByRole('article', { name: 'Java collections in practice' })).toBeTruthy();
    expect(screen.queryByRole('article', { name: 'Making sense of probability' })).toBeNull();
  });

  it('leaving a joined room calls the backend', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /^Joined/ }));
    const room = await screen.findByRole('article', { name: 'Making sense of probability' });
    fireEvent.click(within(room).getByRole('button', { name: 'Leave' }));
    await waitFor(() => expect(leaveStudyRoom).toHaveBeenCalledWith(2));
  });
});
