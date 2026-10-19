import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../src/App';
import i18n from '../src/i18n';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { CurrentUser } from '../src/types/currentUser';
import type { Role } from '../src/types/enums';

const DEGREES = [{ id: 1, name: 'Test Degree', curricularYears: 3 }];

function userWith(role: Role): CurrentUser {
  return { number: 10, name: 'Test User', institutionalEmail: 'test.user@test.com', role, mustChangePassword: false, personalityTestRequired: false };
}

function backend(user: CurrentUser, passwordReply: { status: number; body: unknown } = { status: 200, body: user }) {
  const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
    const url = String(input);
    if (url === '/api/users/me') return new Response(JSON.stringify(user), { status: 200 });
    if (url === '/api/degrees/') return new Response(JSON.stringify(DEGREES), { status: 200 });
    if (url === '/api/users/change-password' && init?.method === 'PUT') {
      return new Response(JSON.stringify(passwordReply.body), { status: passwordReply.status });
    }
    throw new Error(`Unexpected request: ${url}`);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function start(path: string) {
  window.history.replaceState({}, '', path);
  render(<StudyMatchProvider><App /></StudyMatchProvider>);
}

beforeEach(async () => {
  await i18n.changeLanguage('en');
  document.cookie = 'XSRF-TOKEN=test-token';
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', '/');
});

describe('role features', () => {
  it('opens the student registration for staff, not the details of a student', async () => {
    const fetchMock = backend(userWith('STAFF'));
    start('/students/register');

    expect(await screen.findByLabelText('Degree programme')).toBeTruthy();
    expect(fetchMock.mock.calls.some(([url]) => String(url) === '/api/students/register')).toBe(false);
  });

  it('opens the account creation for the admin with the institution domain', async () => {
    backend(userWith('ADMIN'));
    start('/accounts/new');

    expect(await screen.findByText('@test.com')).toBeTruthy();
  });

  it('does not open the account creation for staff', async () => {
    backend(userWith('STAFF'));
    start('/accounts/new');

    await waitFor(() => expect(window.location.pathname).toBe('/'));
    expect(screen.queryByText('@test.com')).toBeNull();
  });

  it('changes the password from the account menu with the current password', async () => {
    const fetchMock = backend(userWith('TEACHER'));
    start('/change-password');

    fireEvent.change(await screen.findByLabelText('Current password'), { target: { value: 'OldPassword1' } });
    fireEvent.change(screen.getByLabelText('New password'), { target: { value: 'NewPassword1' } });
    fireEvent.change(screen.getByLabelText('Confirm password'), { target: { value: 'NewPassword1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Password changed.')).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith('/api/users/change-password', expect.objectContaining({
      body: JSON.stringify({ currentPassword: 'OldPassword1', newPassword: 'NewPassword1' }),
    }));
    await waitFor(() => expect(window.location.pathname).toBe('/'));
  });

  it('shows the backend message for a wrong current password', async () => {
    backend(userWith('STAFF'), { status: 400, body: { code: 'CURRENT_PASSWORD_INCORRECT' } });
    start('/change-password');

    fireEvent.change(await screen.findByLabelText('Current password'), { target: { value: 'Wrong1234' } });
    fireEvent.change(screen.getByLabelText('New password'), { target: { value: 'NewPassword1' } });
    fireEvent.change(screen.getByLabelText('Confirm password'), { target: { value: 'NewPassword1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByRole('alert')).toBeTruthy();
    expect(window.location.pathname).toBe('/change-password');
  });
});
