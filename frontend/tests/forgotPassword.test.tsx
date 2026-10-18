import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { LoginPage } from '../src/pages/login/LoginPage';
import { StudyMatchProvider } from '../src/studymatch-ui';

const EMAIL = '50001@students.studymatch.com';

interface BackendReply {
  status: number;
  body?: unknown;
}

function mockBackend(resetReply: BackendReply = { status: 204 }) {
  const fetchMock = vi.fn<typeof fetch>(async url => {
    const reply = url === '/api/users/reset-password' ? resetReply : { status: 204 };
    return new Response(reply.body === undefined ? null : JSON.stringify(reply.body), { status: reply.status });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderLoginPage() {
  render(<StudyMatchProvider><LoginPage onLoggedIn={vi.fn()} /></StudyMatchProvider>);
}

async function askForCode() {
  fireEvent.click(screen.getByRole('button', { name: 'Forgot password?' }));
  fireEvent.change(screen.getByLabelText('Institutional email'), { target: { value: EMAIL } });
  fireEvent.click(screen.getByRole('button', { name: 'Send code' }));
  await screen.findByLabelText('Verification code');
}

function fillNewPassword(code: string, newPassword: string, confirmPassword: string) {
  fireEvent.change(screen.getByLabelText('Verification code'), { target: { value: code } });
  fireEvent.change(screen.getByLabelText('New password'), { target: { value: newPassword } });
  fireEvent.change(screen.getByLabelText('Confirm password'), { target: { value: confirmPassword } });
  fireEvent.click(screen.getByRole('button', { name: 'Reset password' }));
}

function requestsTo(fetchMock: ReturnType<typeof mockBackend>, path: string) {
  return fetchMock.mock.calls.filter(([url]) => url === path);
}

beforeEach(async () => {
  await i18n.changeLanguage('en');
  document.cookie = 'XSRF-TOKEN=test-csrf-token';
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('forgot password', () => {
  it('sends a code for the institutional email without saying whether the account exists', async () => {
    const fetchMock = mockBackend();
    renderLoginPage();

    await askForCode();

    expect(screen.getByText(`If an account exists for ${EMAIL}`, { exact: false })).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith('/api/users/forgot-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'test-csrf-token' },
      credentials: 'include',
      body: JSON.stringify({ email: EMAIL }),
    });
  });

  it('changes the password with the code and goes back to the login page', async () => {
    const fetchMock = mockBackend();
    renderLoginPage();

    await askForCode();
    fillNewPassword('123456', 'NewPassword1', 'NewPassword1');

    expect(await screen.findByText('Password updated. Log in with your new password.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Log in' })).toBeTruthy();
    expect(requestsTo(fetchMock, '/api/users/reset-password')[0][1]?.body)
      .toBe(JSON.stringify({ email: EMAIL, code: '123456', newPassword: 'NewPassword1' }));
  });

  it('does not send the new password when the two passwords do not match', async () => {
    const fetchMock = mockBackend();
    renderLoginPage();

    await askForCode();
    fillNewPassword('123456', 'NewPassword1', 'OtherPassword1');

    expect(await screen.findByText("The passwords don't match.")).toBeTruthy();
    expect(requestsTo(fetchMock, '/api/users/reset-password')).toHaveLength(0);
  });

  it('shows the backend message for a wrong or expired code', async () => {
    mockBackend({ status: 400, body: { code: 'RESET_CODE_INVALID' } });
    renderLoginPage();

    await askForCode();
    fillNewPassword('000000', 'NewPassword1', 'NewPassword1');

    expect(await screen.findByText('This code is invalid or has expired. Check it or request a new one.')).toBeTruthy();
  });

  it('shows the password policy errors the backend reports under the new password', async () => {
    mockBackend({
      status: 400,
      body: { code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'newPassword', code: 'SIZE', params: { min: 8, max: 72 } }] },
    });
    renderLoginPage();

    await askForCode();
    fillNewPassword('123456', 'short1', 'short1');

    expect(await screen.findByText('Use between 8 and 72 characters or items.')).toBeTruthy();
  });

  it('shows the password requirements while typing', async () => {
    mockBackend();
    renderLoginPage();

    await askForCode();

    expect(screen.getByText('At least 8 characters')).toBeTruthy();
    expect(screen.getByText('At least one letter')).toBeTruthy();
    expect(screen.getByText('At least one number')).toBeTruthy();
  });

  it('asks the backend for a new code', async () => {
    const fetchMock = mockBackend();
    renderLoginPage();

    await askForCode();
    fireEvent.click(screen.getByRole('button', { name: 'Send a new code' }));

    expect(await screen.findByText('If an account exists for this email, we sent a new code. The previous one no longer works.')).toBeTruthy();
    expect(requestsTo(fetchMock, '/api/users/forgot-password')).toHaveLength(2);
  });

  it('goes back to the login page', async () => {
    mockBackend();
    renderLoginPage();

    await askForCode();
    fireEvent.click(screen.getByRole('button', { name: 'Back to log in' }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Log in' })).toBeTruthy());
  });
});
