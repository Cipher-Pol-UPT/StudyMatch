import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { ChangePasswordPage } from '../src/pages/password/ChangePasswordPage';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { CurrentUser } from '../src/types/currentUser';

const user: CurrentUser = {
  number: 50001, name: 'Test Student', institutionalEmail: '50001@students.test.com', role: 'STUDENT',
  mustChangePassword: false, personalityTestRequired: true,
};

interface BackendReply {
  status: number;
  body?: unknown;
}

function mockBackend(reply: BackendReply = { status: 200, body: user }) {
  const fetchMock = vi.fn<typeof fetch>(async url => {
    if (url === '/api/users/logout') return new Response(null, { status: 204 });
    return new Response(JSON.stringify(reply.body), { status: reply.status });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderPage() {
  const onPasswordChanged = vi.fn();
  const onLoggedOut = vi.fn();
  render(<StudyMatchProvider><ChangePasswordPage onPasswordChanged={onPasswordChanged} onLoggedOut={onLoggedOut} /></StudyMatchProvider>);
  return { onPasswordChanged, onLoggedOut };
}

function choosePassword(newPassword: string, confirmPassword: string) {
  fireEvent.change(screen.getByLabelText('New password'), { target: { value: newPassword } });
  fireEvent.change(screen.getByLabelText('Confirm password'), { target: { value: confirmPassword } });
  fireEvent.click(screen.getByRole('button', { name: 'Save' }));
}

beforeEach(async () => {
  await i18n.changeLanguage('en');
  document.cookie = 'XSRF-TOKEN=test-csrf-token';
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('first password change', () => {
  it('does not ask for the one-time password again', () => {
    mockBackend();
    renderPage();

    expect(screen.getByRole('heading', { name: 'Choose your password' })).toBeTruthy();
    expect(screen.queryByLabelText('Current password')).toBeNull();
  });

  it('saves the new password and continues with the updated user', async () => {
    const fetchMock = mockBackend();
    const { onPasswordChanged } = renderPage();

    choosePassword('NewPassword1', 'NewPassword1');

    await waitFor(() => expect(onPasswordChanged).toHaveBeenCalledWith(user));
    expect(fetchMock).toHaveBeenCalledWith('/api/users/change-password', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ newPassword: 'NewPassword1' }),
    }));
    expect(await screen.findByText('Password changed.')).toBeTruthy();
  });

  it('shows under the confirmation that the passwords do not match, without calling the backend', async () => {
    const fetchMock = mockBackend();
    renderPage();

    choosePassword('NewPassword1', 'OtherPassword1');

    expect(await screen.findByText("The passwords don't match.")).toBeTruthy();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('shows the password policy errors the backend reports', async () => {
    mockBackend({ status: 400, body: { code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'newPassword', code: 'PATTERN' }] } });
    renderPage();

    choosePassword('onlyletters', 'onlyletters');

    expect(await screen.findByText('This value has an invalid format.')).toBeTruthy();
    expect(screen.getByText('At least one number')).toBeTruthy();
  });

  it('lets the user log out instead', async () => {
    const fetchMock = mockBackend();
    const { onLoggedOut } = renderPage();

    fireEvent.click(screen.getByRole('button', { name: 'Log out' }));

    await waitFor(() => expect(onLoggedOut).toHaveBeenCalled());
    expect(fetchMock).toHaveBeenCalledWith('/api/users/logout', expect.objectContaining({ method: 'POST' }));
  });
});
