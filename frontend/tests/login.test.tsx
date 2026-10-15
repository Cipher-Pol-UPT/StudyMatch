import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { LoginPage } from '../src/pages/login/LoginPage';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { Session } from '../src/types/session';

const studentSession: Session = {
  name: 'Maria Costa',
  role: 'STUDENT',
  mustChangePassword: false,
  personalityTestPending: false,
};

function mockLoginResponse(status: number, body: unknown = {}) {
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
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('login page', () => {
  it('highlights empty fields without calling the backend', async () => {
    const fetchMock = mockLoginResponse(200, studentSession);
    renderLoginPage();

    fillAndSubmit('', '');

    await screen.findByText('Check the highlighted fields.');
    expect(screen.getAllByText('This field is required.')).toHaveLength(2);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('warns about an invalid email format', async () => {
    mockLoginResponse(200, studentSession);
    renderLoginPage();

    fillAndSubmit('not-an-email', 'secret123');

    await screen.findByText('Enter a valid email address.');
  });

  it('sends the credentials and reports the session on success', async () => {
    const fetchMock = mockLoginResponse(200, studentSession);
    const onLoggedIn = renderLoginPage();

    fillAndSubmit('  2410231@students.upt.pt ', 'secret123');

    await waitFor(() => expect(onLoggedIn).toHaveBeenCalledWith(studentSession));
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ email: '2410231@students.upt.pt', password: 'secret123' }),
    }));
  });

  it('shows a generic message when the credentials are wrong', async () => {
    mockLoginResponse(401);
    const onLoggedIn = renderLoginPage();

    fillAndSubmit('2410231@students.upt.pt', 'wrong-password');

    await screen.findByText('The email or password is incorrect. Please try again.');
    expect(onLoggedIn).not.toHaveBeenCalled();
  });

  it('tells the user when the account is blocked', async () => {
    mockLoginResponse(403);
    const onLoggedIn = renderLoginPage();

    fillAndSubmit('2410231@students.upt.pt', 'secret123');

    await screen.findByText(/Your account is blocked/);
    expect(onLoggedIn).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Back' }));
    expect(screen.getByRole('button', { name: 'Log in' })).toBeTruthy();
  });

  it('lets the user try again after a network error', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch'); }));
    const onLoggedIn = renderLoginPage();

    fillAndSubmit('2410231@students.upt.pt', 'secret123');

    await screen.findByText('Something went wrong. Please try again.');
    expect(onLoggedIn).not.toHaveBeenCalled();
    expect((screen.getByRole('button', { name: 'Log in' }) as HTMLButtonElement).disabled).toBe(false);
  });
});
