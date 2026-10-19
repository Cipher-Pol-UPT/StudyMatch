import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../src/App';
import i18n from '../src/i18n';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { CurrentUser } from '../src/types/currentUser';

const student: CurrentUser = {
  number: 50001,
  name: 'Test Student',
  institutionalEmail: '50001@students.test.com',
  role: 'STUDENT',
  mustChangePassword: false,
  personalityTestRequired: false,
};

function mockBackend(user: CurrentUser, logoutStatus = 204) {
  const fetchMock = vi.fn<typeof fetch>(async url => {
    if (url === '/api/users/logout') {
      return new Response(logoutStatus === 204 ? null : JSON.stringify({}), { status: logoutStatus });
    }
    return new Response(JSON.stringify(user), { status: 200 });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderApp() {
  render(<StudyMatchProvider><App /></StudyMatchProvider>);
}

beforeEach(async () => {
  await i18n.changeLanguage('en');
  document.cookie = 'XSRF-TOKEN=test-csrf-token';
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('log out', () => {
  it('ends the session in the backend and goes back to the login page with a confirmation', async () => {
    const fetchMock = mockBackend(student);
    renderApp();

    fireEvent.click(await screen.findByRole('button', { name: 'Log out' }));

    expect(await screen.findByText('You have logged out.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Log in' })).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith('/api/users/logout', {
      method: 'POST',
      headers: { 'X-XSRF-TOKEN': 'test-csrf-token' },
      credentials: 'include',
      body: undefined,
    });
  });

  it('can be used before changing a one-time password', async () => {
    mockBackend({ ...student, mustChangePassword: true });
    renderApp();

    await screen.findByText('Change password');

    expect(screen.getByRole('button', { name: 'Log out' })).toBeTruthy();
  });

  it('keeps the user logged in when the backend cannot end the session', async () => {
    mockBackend(student, 500);
    renderApp();

    fireEvent.click(await screen.findByRole('button', { name: 'Log out' }));

    expect(await screen.findByText('Something went wrong. Please try again.')).toBeTruthy();
    expect(screen.getByText('Hello, Test Student.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Log in' })).toBeNull();
  });
});
