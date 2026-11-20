import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { logout } from '../src/api/auth';
import { getMyProfile } from '../src/api/students';
import i18n from '../src/i18n';
import { StudyMatchProvider } from '../src/studymatch-ui';
import { StudentArea } from '../src/pages/student/StudentArea';
import type { CurrentUser } from '../src/types/currentUser';

vi.mock('../src/api/auth', () => ({ logout: vi.fn(async () => undefined) }));
vi.mock('../src/api/students', () => ({
  getMyProfile: vi.fn(async () => ({
    number: 45123, name: 'Ana Silva', banner: null, pfp: null, aboutMe: null, skills: [], interests: [],
    languages: ['PORTUGUESE'], tutorBadges: [], achievements: [],
    privateProfile: {
      institutionalEmail: 'ana@school.pt', degree: 'Informatics', curricularYear: 2, personalityProfile: null,
      attendance: 90, hasAcademicHistory: true,
    },
  })),
}));

const user: CurrentUser = { number: 1, name: 'Ana Silva', institutionalEmail: 'ana@school.pt', role: 'STUDENT', mustChangePassword: false, personalityTestRequired: false };

function renderArea() {
  const onLoggedOut = vi.fn();
  render(<StudyMatchProvider><MemoryRouter><StudentArea currentUser={user} onLoggedOut={onLoggedOut} /></MemoryRouter></StudyMatchProvider>);
  return onLoggedOut;
}

afterEach(cleanup);
beforeEach(async () => {
  vi.clearAllMocks();
  await i18n.changeLanguage('en');
});

describe('StudentArea', () => {
  it('lists the sections in the side menu as real links and marks Home as current', () => {
    renderArea();
    expect(screen.getByRole('link', { name: 'StudyRooms' }).getAttribute('href')).toBe('/study-rooms');
    expect(screen.getByRole('link', { name: 'Home' }).getAttribute('aria-current')).toBe('page');
  });

  it('shows the big five traits on the back of the student pass while there is no group', async () => {
    const profile = await getMyProfile();
    vi.mocked(getMyProfile).mockResolvedValueOnce({
      ...profile,
      privateProfile: { ...profile.privateProfile!, personalityProfile: { openness: 0.4, conscientiousness: 0.9, extraversion: 0.5, agreeableness: 0.6, neuroticism: 0.2 } },
    });
    renderArea();
    const pass = await screen.findByRole('button', { name: /Ana Silva’s student pass/ });
    const back = within(pass.querySelector('.sm-pass--back') as HTMLElement);
    expect(back.getByText('Conscientiousness')).toBeTruthy();
    expect(back.getByText('90%')).toBeTruthy();
    expect(back.getByText('Neuroticism')).toBeTruthy();
    expect(back.queryByText('No group yet')).toBeNull();
    fireEvent.click(pass);
    expect(pass.getAttribute('aria-label')).toContain('Showing the personality profile');
  });

  it('opens the profile page from the avatar menu', async () => {
    renderArea();
    expect(screen.queryByRole('menuitem', { name: 'My profile' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Account menu' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'My profile' }));
    expect(await screen.findByRole('button', { name: 'Edit profile' })).toBeTruthy();
  });

  it('opens change password from the avatar menu and from the profile', async () => {
    renderArea();
    fireEvent.click(screen.getByRole('button', { name: 'Account menu' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Change password' }));
    expect(await screen.findByLabelText('Current password')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Account menu' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'My profile' }));
    expect((await screen.findByRole('link', { name: 'Change password' })).getAttribute('href')).toBe('/change-password');
  });

  it('opens all the achievements from the badges on the profile', async () => {
    renderArea();
    fireEvent.click(screen.getByRole('button', { name: 'Account menu' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'My profile' }));
    fireEvent.click(await screen.findByRole('link', { name: 'See all' }));
    expect(await screen.findByRole('heading', { name: 'Still to earn' })).toBeTruthy();
  });

  it('opens the study rooms demo from the student navigation', async () => {
    renderArea();
    fireEvent.click(screen.getByRole('link', { name: 'StudyRooms' }));
    expect(await screen.findByRole('heading', { name: 'StudyRooms' })).toBeTruthy();
    expect(screen.getByRole('button', { name: /^Joined/ })).toBeTruthy();
    expect(screen.queryByText('This page is not available yet.')).toBeNull();
  });

  it('logs out through the API and then clears the session', async () => {
    const onLoggedOut = renderArea();
    fireEvent.click(screen.getByRole('button', { name: 'Account menu' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Log out' }));
    await waitFor(() => expect(onLoggedOut).toHaveBeenCalledTimes(1));
    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('stays logged in and warns when the session could not be ended', async () => {
    vi.mocked(logout).mockRejectedValueOnce(new TypeError('Failed to fetch'));
    const onLoggedOut = renderArea();
    fireEvent.click(screen.getByRole('button', { name: 'Account menu' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Log out' }));
    expect(await screen.findByText('Could not end your session. Check your connection and try again.')).toBeTruthy();
    expect(onLoggedOut).not.toHaveBeenCalled();
  });

  it('greets the student and shows what to do next instead of the attendance', async () => {
    renderArea();
    expect(screen.getByRole('heading', { name: 'Hello, Ana Silva.' })).toBeTruthy();
    expect(await screen.findByText(/Informatics/)).toBeTruthy();
    expect(screen.queryByText('90')).toBeNull();
    expect(screen.getByRole('heading', { name: 'Get started' })).toBeTruthy();
    expect(screen.getByText('Write a few words about you')).toBeTruthy();
    // not a tutor, so no achievement can be earned yet
    expect(screen.queryByRole('heading', { name: 'Next achievement' })).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Tutoring' })).toBeNull();
  });

  it('shows tutors their subjects and hides the checklist once the profile is complete', async () => {
    const profile = await getMyProfile();
    vi.mocked(getMyProfile).mockResolvedValueOnce({
      ...profile, pfp: 'data:image/png;base64,AA', aboutMe: 'Hi', skills: ['Java'], interests: ['Games'],
      tutorBadges: [{ subject: 'Programming I', averageRating: 4.5, ratingCount: 2 }],
    });
    renderArea();
    expect(await screen.findByRole('heading', { name: 'Tutoring' })).toBeTruthy();
    expect(screen.getByText('Programming I')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'New StudyRoom' }).getAttribute('href')).toBe('/study-rooms');
    expect(screen.queryByRole('heading', { name: 'Get started' })).toBeNull();
    expect(screen.getByRole('heading', { name: 'Next achievement' })).toBeTruthy();
    expect(screen.getByText('Tutor your first StudyRoom.')).toBeTruthy();
    expect(screen.getByText('Only StudyRooms with at least 2 confirmed attendees count.')).toBeTruthy();
  });

  it('shows the student pass once the student has a personality profile', async () => {
    const profile = await getMyProfile();
    vi.mocked(getMyProfile).mockResolvedValueOnce({
      ...profile,
      privateProfile: { ...profile.privateProfile!, personalityProfile: { openness: 0.4, conscientiousness: 0.9, extraversion: 0.5, agreeableness: 0.6, neuroticism: 0.2 } },
    });
    renderArea();
    expect(await screen.findByRole('button', { name: /Ana Silva’s student pass/ })).toBeTruthy();
    expect(screen.getByText('45123')).toBeTruthy();
    expect(screen.queryByText(/Student no\./)).toBeNull();
  });

  it('always shows this week, and says where tasks will come from while there are none', () => {
    renderArea();
    expect(screen.getByRole('heading', { name: 'This week' })).toBeTruthy();
    expect(screen.getAllByRole('columnheader')).toHaveLength(7);
    expect(screen.getByText('Deadlines and StudyRooms will show up here.')).toBeTruthy();
  });
});