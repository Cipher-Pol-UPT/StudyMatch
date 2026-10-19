import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getCurrentUser, login } from '../src/api/auth';
import i18n from '../src/i18n';
import { LoginPage } from '../src/pages/login/LoginPage';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { CurrentUser } from '../src/types/currentUser';

const student: CurrentUser = {
  number: 50001,
  name: 'Maria Costa',
  institutionalEmail: '50001@students.studymatch.com',
  role: 'STUDENT',
  mustChangePassword: false,
  personalityTestRequired: false,
};

function mockBackendResponse(status: number, body: unknown) {
  const fetchMock = vi.fn(async () => new Response(JSON.stringify(body), { status }));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderLoginPage() {
  const onLoggedIn = vi.fn();
  render(<StudyMatchProvider><LoginPage onLoggedIn={onLoggedIn} /></StudyMatchProvider>);
  return onLoggedIn;
}

function fillAndSubmit(email: string, password: string) {
  fireEvent.change(screen.getByLabelText('Institutional email'), { target: { value: email } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } });
  fireEvent.click(screen.getByRole('button', { name: 'Log in' }));
}

beforeEach(async () => {
  await i18n.changeLanguage('en');
  document.cookie = 'XSRF-TOKEN=test-csrf-token';
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('login page', () => {
  it('logs in through the backend with the CSRF token and the session cookie', async () => {
    const fetchMock = mockBackendResponse(200, student);
    const onLoggedIn = renderLoginPage();

    fillAndSubmit('50001@students.studymatch.com', 'secret123');

    await waitFor(() => expect(onLoggedIn).toHaveBeenCalledWith(student));
    expect(fetchMock).toHaveBeenCalledWith('/api/users/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'test-csrf-token' },
      credentials: 'include',
      body: JSON.stringify({ email: '50001@students.studymatch.com', password: 'secret123' }),
    });
  });

  it('lets password managers fill the email and the password', () => {
    renderLoginPage();

    expect(screen.getByLabelText('Institutional email').getAttribute('autocomplete')).toBe('username');
    expect(screen.getByLabelText('Password').getAttribute('autocomplete')).toBe('current-password');
  });

  it('shows the backend message for wrong credentials', async () => {
    mockBackendResponse(401, { code: 'INVALID_CREDENTIALS' });
    const onLoggedIn = renderLoginPage();

    fillAndSubmit('50001@students.studymatch.com', 'wrong-password');

    await screen.findByText('Incorrect email or password.');
    expect(onLoggedIn).not.toHaveBeenCalled();
  });

  it('highlights the fields the backend rejected', async () => {
    mockBackendResponse(400, {
      code: 'VALIDATION_FAILED',
      fieldErrors: [
        { field: 'email', code: 'EMAIL' },
        { field: 'password', code: 'NOT_BLANK' },
      ],
    });
    renderLoginPage();

    fillAndSubmit('not-an-email', '');

    await screen.findByText('Check the highlighted fields.');
    expect(screen.getByText('Enter a valid email address.')).toBeTruthy();
    expect(screen.getByText('This field is required.')).toBeTruthy();
  });

  it('tells the user when the account is blocked', async () => {
    mockBackendResponse(403, { code: 'ACCOUNT_BLOCKED' });
    const onLoggedIn = renderLoginPage();

    fillAndSubmit('50001@students.studymatch.com', 'secret123');

    await screen.findByText('Your account is blocked.');
    expect(onLoggedIn).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Back' }));
    expect(screen.getByRole('button', { name: 'Log in' })).toBeTruthy();
  });

  it('lets the user try again after a network error', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch'); }));
    const onLoggedIn = renderLoginPage();

    fillAndSubmit('50001@students.studymatch.com', 'secret123');

    await screen.findByText('Something went wrong. Please try again.');
    expect(onLoggedIn).not.toHaveBeenCalled();
    expect((screen.getByRole('button', { name: 'Log in' }) as HTMLButtonElement).disabled).toBe(false);
  });
});

describe('current user', () => {
  it('returns the logged in user', async () => {
    const fetchMock = mockBackendResponse(200, student);

    await expect(getCurrentUser()).resolves.toEqual(student);
    expect(fetchMock).toHaveBeenCalledWith('/api/users/me', expect.objectContaining({
      method: 'GET',
      credentials: 'include',
    }));
  });

  it('returns no user when there is no session', async () => {
    mockBackendResponse(401, { code: 'UNAUTHENTICATED' });

    await expect(getCurrentUser()).resolves.toBeNull();
  });

  it('asks the backend for a CSRF token before the first login', async () => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
    const fetchMock = vi.fn<typeof fetch>(async url => {
      if (url === '/api/users/me') {
        document.cookie = 'XSRF-TOKEN=token-from-backend';
        return new Response(JSON.stringify({ code: 'UNAUTHENTICATED' }), { status: 401 });
      }
      return new Response(JSON.stringify(student), { status: 200 });
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(login({ email: student.institutionalEmail, password: 'secret123' })).resolves.toEqual(student);

    const [firstUrl, secondUrl] = fetchMock.mock.calls.map(([url]) => url);
    const loginHeaders = fetchMock.mock.calls[1][1]?.headers as Record<string, string>;
    expect([firstUrl, secondUrl]).toEqual(['/api/users/me', '/api/users/login']);
    expect(loginHeaders['X-XSRF-TOKEN']).toBe('token-from-backend');
  });
});
