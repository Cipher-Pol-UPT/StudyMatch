import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getNotifications, getUnreadNotifications, markAllNotificationsRead, markNotificationRead } from '../src/api/notifications';
import i18n from '../src/i18n';
import { NewAchievement } from '../src/pages/notifications/NewAchievement';
import { NotificationsBell } from '../src/pages/notifications/NotificationsBell';
import { NotificationsPage } from '../src/pages/notifications/NotificationsPage';
import { NotificationsProvider } from '../src/pages/notifications/NotificationsProvider';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { Role } from '../src/types/enums';
import type { NotificationData } from '../src/types/notifications';

// tests/setup.ts mocks the api; each test gives it its answers here

const at = (minutesAgo: number) => new Date(Date.now() - minutesAgo * 60000).toISOString();
const cancelled: NotificationData = { id: 3, type: 'STUDYROOM_CANCELLED', params: { subject: 'Programming I', startsAt: '2096-12-03T14:00:00', reason: 'Tutor is sick' },
  target: 'STUDYROOM', targetId: 40, createdAt: at(5), read: false };
const group: NotificationData = { id: 2, type: 'GROUP_PUBLISHED', params: { subject: 'Databases', assignment: 'Project', number: 3 },
  target: 'ASSIGNMENT', targetId: 9, createdAt: at(120), read: false };
const unblocked: NotificationData = { id: 1, type: 'ACCOUNT_UNBLOCKED', params: {}, target: null, targetId: null, createdAt: at(60 * 24 * 3), read: true };
const achievement: NotificationData = { id: 4, type: 'ACHIEVEMENT_EARNED', params: { achievement: 'FIRST_STUDYROOM' }, target: 'ACHIEVEMENT',
  targetId: null, createdAt: at(1), read: false };
const application: NotificationData = { id: 5, type: 'TUTOR_APPLICATION_SUBMITTED', params: { subject: 'Programming I', subjectId: 2, student: 'Ana Silva', number: 50001 },
  target: 'TUTOR_APPLICATION', targetId: 11, createdAt: at(2), read: false };

const page = (content: NotificationData[], totalPages = 1, number = 0) => ({ content, page: number, size: 20, totalElements: content.length, totalPages });

function answer(first: NotificationData[], unread = first.filter(item => !item.read).length, older?: NotificationData[]) {
  vi.mocked(getNotifications).mockImplementation(async (number = 0) => number === 0 ? page(first, older ? 2 : 1) : page(older ?? [], 2, number));
  vi.mocked(getUnreadNotifications).mockResolvedValue(unread);
}

function renderAt(path: string, role: Role = 'STUDENT') {
  render(
    <StudyMatchProvider>
      <MemoryRouter initialEntries={[path]}>
        <NotificationsProvider role={role}>
          <NotificationsBell />
          <Routes>
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/study-rooms" element={<p>StudyRooms page</p>} />
            <Route path="/groups" element={<p>Study groups page</p>} />
            <Route path="/tutor-applications" element={<p>Tutor applications page</p>} />
            <Route path="/achievements" element={<p>Achievements page</p>} />
            <Route path="/" element={<p>Home</p>} />
          </Routes>
          <NewAchievement />
        </NotificationsProvider>
      </MemoryRouter>
    </StudyMatchProvider>,
  );
}

const list = () => document.querySelector('.notifications-page .sm-notifs') as HTMLElement;
const titles = () => [...list().querySelectorAll('.sm-notifs__item-title')].map(title => title.textContent);
const bellButton = () => screen.getByRole('button', { name: /notifications/i });

beforeEach(async () => {
  await i18n.changeLanguage('en');
  localStorage.clear();
});
afterEach(() => {
  cleanup();
  vi.mocked(markNotificationRead).mockClear();
  vi.mocked(markAllNotificationsRead).mockClear();
});

describe('notifications', () => {
  it('lists them newest first, each with its message in your language, when it came and whether it was read', async () => {
    answer([cancelled, group, unblocked]);
    renderAt('/notifications');

    await screen.findByText('A StudyRoom was cancelled');
    expect(titles()).toEqual(['A StudyRoom was cancelled', 'Your group is ready', 'Your account is active again']);
    expect(within(list()).getByText(/^Programming I on .+ won't happen\. Reason: Tutor is sick$/)).toBeTruthy();
    expect(within(list()).getByText("You're in group 3 for Project in Databases.")).toBeTruthy();
    expect(within(list()).getByText('5 min. ago')).toBeTruthy();
    expect(within(list()).getAllByLabelText('Unread')).toHaveLength(2);
  });

  it("says you're all caught up when there are none", async () => {
    answer([]);
    renderAt('/notifications');
    expect(await screen.findByRole('heading', { name: 'Notifications' })).toBeTruthy();
    expect(await within(list()).findByText("You're all caught up.")).toBeTruthy();
  });

  it('opening one marks it as read and opens what it is about', async () => {
    answer([cancelled, group]);
    renderAt('/notifications');
    fireEvent.click(await screen.findByRole('button', { name: /Your group is ready/ }));

    expect(await screen.findByText('Study groups page')).toBeTruthy();
    expect(markNotificationRead).toHaveBeenCalledWith(2);
    expect(bellButton().getAttribute('aria-label')).toBe('Notifications, 1 unread');
  });

  it('mark all as read sends it once and brings the count to zero', async () => {
    answer([cancelled, group]);
    renderAt('/notifications');
    await screen.findByText('A StudyRoom was cancelled');
    fireEvent.click(within(list()).getByRole('button', { name: 'Mark all as read' }));

    expect(markAllNotificationsRead).toHaveBeenCalledTimes(1);
    expect(within(list()).queryAllByLabelText('Unread')).toHaveLength(0);
    expect(bellButton().getAttribute('aria-label')).toBe('Notifications');
  });

  it("the bell shows the backend's unread count and the newest ones, and opens one from there", async () => {
    answer([application, cancelled], 25);
    renderAt('/', 'TEACHER');

    await waitFor(() => expect(bellButton().getAttribute('aria-label')).toBe('Notifications, 25 unread'));
    expect(screen.getByText('9+')).toBeTruthy();
    fireEvent.click(bellButton());
    fireEvent.click(await screen.findByRole('button', { name: /New tutor application/ }));

    // a teacher's tutor application opens their applications
    expect(await screen.findByText('Tutor applications page')).toBeTruthy();
    expect(markNotificationRead).toHaveBeenCalledWith(5);
    // the bell closes once one is picked
    await waitFor(() => expect(screen.queryByRole('button', { name: /New tutor application/ })).toBeNull());
  });

  it('the bell links to the whole list', async () => {
    answer([cancelled]);
    renderAt('/');
    fireEvent.click(bellButton());
    fireEvent.click(await screen.findByRole('link', { name: 'See all notifications' }));

    expect(await screen.findByRole('heading', { name: 'Notifications' })).toBeTruthy();
  });

  it('shows only the unread ones when asked, and says when there are none left', async () => {
    answer([cancelled, group, unblocked]);
    renderAt('/notifications');
    fireEvent.click(await screen.findByRole('radio', { name: 'Unread (2)' }));

    expect(titles()).toEqual(['A StudyRoom was cancelled', 'Your group is ready']);
    fireEvent.click(within(list()).getByRole('button', { name: 'Mark all as read' }));
    expect(await within(list()).findByText('No unread notifications.')).toBeTruthy();
    fireEvent.click(screen.getByRole('radio', { name: 'All' }));
    expect(titles()).toHaveLength(3);
  });

  it('shows the older ones a page at a time', async () => {
    answer([cancelled], 1, [unblocked]);
    renderAt('/notifications');
    fireEvent.click(await screen.findByRole('button', { name: 'Show older' }));

    expect(await within(list()).findByText('Your account is active again')).toBeTruthy();
    expect(getNotifications).toHaveBeenCalledWith(1);
    expect(screen.queryByRole('button', { name: 'Show older' })).toBeNull();
  });

  it('a new achievement shows its badge and a toast once, and stays in the list', async () => {
    answer([achievement, cancelled]);
    renderAt('/notifications');

    const badge = await screen.findByRole('dialog', { name: 'First StudyRoom' });
    expect(within(badge).getByText('Tutor your first StudyRoom.')).toBeTruthy();
    expect(within(badge).getByText(/^Earned on/)).toBeTruthy();
    // the toast and the item in the list
    await waitFor(() => expect(screen.getAllByText('New achievement: First StudyRoom')).toHaveLength(2));
    expect(within(list()).getByText('New achievement: First StudyRoom')).toBeTruthy();
    fireEvent.click(within(badge).getAllByRole('button', { name: 'Close' }).at(-1)!);

    // opening the app again doesn't show it twice
    cleanup();
    renderAt('/notifications');
    await screen.findByText('A StudyRoom was cancelled');
    expect(screen.queryByRole('dialog', { name: 'First StudyRoom' })).toBeNull();
  });

  it('has every message in Portuguese too', async () => {
    await i18n.changeLanguage('pt');
    answer([group]);
    renderAt('/notifications');
    expect(await screen.findByText('O seu grupo está pronto')).toBeTruthy();
    expect(screen.getByText('Está no grupo 3 de Project em Databases.')).toBeTruthy();
  });
});
