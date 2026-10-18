import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { logout } from '../src/api/auth';
import i18n from '../src/i18n';
import { StudentArea } from '../src/pages/student/StudentArea';
import type { CurrentUser } from '../src/types/currentUser';

vi.mock('../src/api/auth', () => ({ logout: vi.fn(async () => undefined) }));
vi.mock('../src/api/students', () => ({
  getMyProfile: vi.fn(async () => ({ programme: 'Informatics', curricularYear: 2, studentNumber: '45123', attendance: 90, achievements: [] })),
}));

const user: CurrentUser = { number: 1, name: 'Ana Silva', institutionalEmail: 'ana@school.pt', role: 'STUDENT', mustChangePassword: false, personalityTestRequired: false };

function renderArea() {
  const onLoggedOut = vi.fn();
  render(<MemoryRouter><StudentArea currentUser={user} onLoggedOut={onLoggedOut} /></MemoryRouter>);
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

  it('opens the profile menu from the avatar and navigates from it', async () => {
    renderArea();
    expect(screen.queryByRole('menuitem', { name: 'My profile' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Account menu' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'My profile' }));
    expect(await screen.findByText('This page is not available yet.')).toBeTruthy();
  });

  it('logs out through the API and then clears the session', async () => {
    const onLoggedOut = renderArea();
    fireEvent.click(screen.getByRole('button', { name: 'Account menu' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Log out' }));
    await waitFor(() => expect(onLoggedOut).toHaveBeenCalledTimes(1));
    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('greets the student and shows attendance from the profile', async () => {
    renderArea();
    expect(screen.getByRole('heading', { name: 'Hello, Ana Silva.' })).toBeTruthy();
    expect(await screen.findByText('90')).toBeTruthy();
    expect(screen.getByText(/Informatics/)).toBeTruthy();
  });
});