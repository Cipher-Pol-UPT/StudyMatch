import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../src/App';
import i18n from '../src/i18n';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { CurrentUser } from '../src/types/currentUser';
import type { Role } from '../src/types/enums';
import { connectChatSocket, disconnectChatSocket } from '../src/api/chatSocket';

vi.mock('../src/api/chatSocket', () => ({ onChatSocketReconnect: () => () => undefined,
  // the connection is down, so nobody shows as online
  isChatSocketConnected: () => false, onChatSocketConnection: () => () => undefined, sendTyping: vi.fn(), connectChatSocket: vi.fn(), disconnectChatSocket: vi.fn(async () => {}), subscribeToChatEvents: vi.fn(() => () => undefined) }));

const base: CurrentUser = { number: 50001, name: 'Alex Silva', institutionalEmail: 'alex@school.pt', role: 'STUDENT', mustChangePassword: false, personalityTestRequired: false };
const profile = { number: 50001, name: 'Alex Silva', banner: null, pfp: null, aboutMe: null, skills: [], interests: [], languages: [], tutorBadges: [], achievements: [], privateProfile: null };
const workspaces = [
  { role: 'ADMIN' as const, title: 'Administrator workspace', links: ['Create account', 'Block account', 'Unblock account'] },
  { role: 'STAFF' as const, title: 'Staff workspace', links: ['Students', 'Register student', 'Enrolments', 'Analytics'] },
  { role: 'TEACHER' as const, title: 'Teacher workspace', links: ['Students', 'My subjects', 'Assignments', 'Assignment groups', 'Grades', 'Tutor applications'] },
];

function backend(user: CurrentUser | null, loginUser = user, logoutFails = false) {
  const fetchMock = vi.fn<typeof fetch>(async (input) => {
    const url = String(input);
    if (url === '/api/users/me') return new Response(JSON.stringify(user ?? { code: 'UNAUTHENTICATED' }), { status: user ? 200 : 401 });
    if (url === '/api/users/login') return new Response(JSON.stringify(loginUser), { status: 200 });
    if (url === '/api/users/logout') {
      if (logoutFails) throw new Error('Offline');
      return new Response(null, { status: 204 });
    }
    if (url === '/api/students/me/profile') return new Response(JSON.stringify(profile), { status: 200 });
    throw new Error(`Unexpected request: ${url}`);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function start() { render(<StudyMatchProvider><App /></StudyMatchProvider>); }

beforeEach(async () => {
  await i18n.changeLanguage('en');
  window.history.replaceState({}, '', '/');
  document.cookie = 'XSRF-TOKEN=test-token';
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.clearAllMocks(); window.history.replaceState({}, '', '/'); });

describe('role areas', () => {
  it.each(workspaces)('restores $role into its own home and menu', async ({ role, title, links }) => {
    const fetchMock = backend({ ...base, role });
    start();
    expect(await screen.findByText(title, { selector: 'p' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Hello, Alex Silva.' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Open navigation' }));
    for (const name of links) expect(screen.getByRole('link', { name })).toBeTruthy();
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/students/me/profile'))).toBe(false);
    expect(screen.queryByRole('link', { name: 'StudyRooms' })).toBeNull();
  });

  it.each(workspaces)('opens the $role home after logging in', async ({ role, title }) => {
    backend(null, { ...base, role });
    start();
    await screen.findByRole('button', { name: 'Log in' });
    fireEvent.change(screen.getByLabelText('Institutional email'), { target: { value: base.institutionalEmail } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'Test1234' } });
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }));
    expect(await screen.findByText(title, { selector: 'p' })).toBeTruthy();
  });

  it('lists the staff sections that are not built yet under coming soon', async () => {
    backend({ ...base, role: 'STAFF' });
    start();
    await screen.findByText('Staff workspace', { selector: 'p' });
    expect(screen.getByText('Coming soon')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Degrees' }).classList.contains('sm-nav-item--muted')).toBe(true);
    expect(screen.getByRole('link', { name: 'Students' }).classList.contains('sm-nav-item--muted')).toBe(false);
  });

  it('opens an unimplemented staff feature as an empty state', async () => {
    backend({ ...base, role: 'STAFF' });
    start();
    await screen.findByText('Staff workspace', { selector: 'p' });
    expect(screen.queryByRole('link', { name: /Register and rename degree programmes/ })).toBeNull();
    fireEvent.click(screen.getByRole('link', { name: 'Degrees' }));
    expect(await screen.findByRole('heading', { name: 'Degrees' })).toBeTruthy();
    expect(screen.getByText(i18n.t('placeholder.comingSoon'))).toBeTruthy();
  });

  it.each(['ADMIN', 'TEACHER', 'STUDENT'] as Role[])('does not open staff routes for %s', async role => {
    window.history.replaceState({}, '', '/students/50002');
    const fetchMock = backend({ ...base, role });
    start();
    await screen.findByRole('heading', { name: 'Hello, Alex Silva.' });
    await waitFor(() => expect(window.location.pathname).toBe('/'));
    expect(fetchMock.mock.calls.some(([url]) => String(url) === '/api/students/50002')).toBe(false);
  });

  it('does not send a teacher to the student personality gate', async () => {
    backend({ ...base, role: 'TEACHER', personalityTestRequired: true });
    start();
    expect(await screen.findByText('Teacher workspace', { selector: 'p' })).toBeTruthy();
  });
});

describe('logout', () => {
  it.each(['ADMIN', 'STAFF', 'TEACHER', 'STUDENT'] as Role[])('ends the %s session and returns to login', async role => {
    const fetchMock = backend({ ...base, role });
    start();
    fireEvent.click(await screen.findByRole('button', { name: 'Account menu' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Log out' }));
    expect(await screen.findByRole('button', { name: 'Log in' })).toBeTruthy();
    if (role !== 'STUDENT') expect(await screen.findByText('You have been logged out.')).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith('/api/users/logout', expect.objectContaining({ method: 'POST', credentials: 'include', headers: { 'X-XSRF-TOKEN': 'test-token' } }));
    expect(window.location.pathname).toBe('/');
    // the socket calls happen in an effect, which can land a moment after the screen updates
    await waitFor(() => expect(disconnectChatSocket).toHaveBeenCalledTimes(role === 'STUDENT' || role === 'TEACHER' ? 1 : 0));
  });

  it.each(['ADMIN', 'STAFF', 'TEACHER', 'STUDENT'] as Role[])('opens the chat socket for %s only if they can chat', async role => {
    backend({ ...base, role });
    start();
    expect(await screen.findByRole('button', { name: 'Account menu' })).toBeTruthy();
    await waitFor(() => expect(connectChatSocket).toHaveBeenCalledTimes(role === 'STUDENT' || role === 'TEACHER' ? 1 : 0));
  });

  it('opens the chat socket only after the first password change', async () => {
    backend({ ...base, mustChangePassword: true });
    start();
    expect(await screen.findByRole('heading', { name: 'Choose your password' })).toBeTruthy();
    expect(connectChatSocket).not.toHaveBeenCalled();
  });

  it.each(['ADMIN', 'STAFF', 'TEACHER', 'STUDENT'] as Role[])('keeps the %s first-password gate ahead of the role home', async role => {
    backend({ ...base, role, mustChangePassword: true });
    start();
    expect(await screen.findByRole('heading', { name: 'Choose your password' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Open navigation' })).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Hello, Alex Silva.' })).toBeNull();
  });

  it('does not pretend a failed logout ended the server session', async () => {
    backend({ ...base, role: 'TEACHER' }, undefined, true);
    start();
    fireEvent.click(await screen.findByRole('button', { name: 'Account menu' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Log out' }));
    expect(await screen.findByText('Could not end your session. Check your connection and try again.')).toBeTruthy();
    expect(screen.getByText('Teacher workspace', { selector: 'p' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Log in' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Account menu' }));
    await waitFor(() => expect((screen.getByRole('menuitem', { name: 'Log out' }) as HTMLButtonElement).disabled).toBe(false));
  });
});
