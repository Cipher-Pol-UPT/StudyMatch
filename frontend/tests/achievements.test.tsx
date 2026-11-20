import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import i18n from '../src/i18n';
import { AchievementsPage } from '../src/pages/achievements/AchievementsPage';
import { StudyMatchProvider, type StudentProfile } from '../src/studymatch-ui';

const profile: StudentProfile = {
  name: 'Diogo Student', number: 50003, degree: 'Computer Science', curricularYear: 3, languages: ['PORTUGUESE'],
  achievements: [{ id: 'FIRST_STUDYROOM', earnedAt: '2026-11-08' }],
} as StudentProfile;

function renderPage(shown: StudentProfile | null = profile, failed = false) {
  render(<StudyMatchProvider><MemoryRouter><AchievementsPage profile={shown} failed={failed} /></MemoryRouter></StudyMatchProvider>);
}

afterEach(cleanup);
beforeEach(async () => {
  await i18n.changeLanguage('en');
});

describe('AchievementsPage', () => {
  it('shows the earned achievements with their date and the others with how to earn them', () => {
    renderPage();
    expect(screen.getByText('1 of 4 earned')).toBeTruthy();
    const earned = screen.getByRole('heading', { name: 'Earned' }).parentElement!;
    expect(within(earned).getByText('First StudyRoom')).toBeTruthy();
    expect(within(earned).getByText('Earned on Nov 8, 2026')).toBeTruthy();
    const toEarn = screen.getByRole('heading', { name: 'Still to earn' }).parentElement!;
    expect(within(toEarn).getByText('Tutor 10 StudyRooms.')).toBeTruthy();
    expect(within(toEarn).queryByText('First StudyRoom')).toBeNull();
  });

  it('opens an achievement with how to earn it', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /Mentor/ }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('How to earn it')).toBeTruthy();
    expect(within(dialog).getByText('Tutor 15 different students.')).toBeTruthy();
  });

  it('explains how to earn them when the student has none yet', () => {
    renderPage({ ...profile, achievements: [] });
    expect(screen.getByText('You have no achievements yet. Below is how to earn them.')).toBeTruthy();
    expect(screen.getByText('0 of 4 earned')).toBeTruthy();
  });

  it('links back to the profile', () => {
    renderPage();
    expect(screen.getByRole('link', { name: 'My profile' }).getAttribute('href')).toBe('/profile');
  });
});
