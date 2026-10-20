import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { CreateAccountPage } from '../src/pages/accounts/CreateAccountPage';
import { StudyMatchProvider } from '../src/studymatch-ui';

const CREATED = { number: 7, name: 'Test Teacher', institutionalEmail: 'test.teacher@test.com', type: 'TEACHER' };

function mockBackend(reply: { status: number; body: unknown } = { status: 201, body: CREATED }) {
  const fetchMock = vi.fn<typeof fetch>(async () => new Response(JSON.stringify(reply.body), { status: reply.status }));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderPage() {
  render(<StudyMatchProvider><CreateAccountPage domain="test.com" /></StudyMatchProvider>);
}

function fillAndSubmit() {
  fireEvent.click(screen.getByRole('radio', { name: 'Teacher' }));
  fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Test Teacher' } });
  fireEvent.change(screen.getByLabelText('Personal email'), { target: { value: 'teacher@example.com' } });
  fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'test.teacher' } });
  fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
}

beforeEach(async () => {
  await i18n.changeLanguage('en');
  document.cookie = 'XSRF-TOKEN=test-csrf-token';
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('create account', () => {
  it('shows the institution domain next to the username', () => {
    mockBackend();
    renderPage();

    expect(screen.getByText('@test.com')).toBeTruthy();
    expect(screen.getByText('Only lowercase letters, numbers and dots, for example joao.silva.')).toBeTruthy();
  });

  it('creates the account with the chosen type and shows its details', async () => {
    const fetchMock = mockBackend();
    renderPage();

    fillAndSubmit();

    expect(await screen.findByRole('heading', { name: 'Teacher account created' })).toBeTruthy();
    expect(screen.getByText('test.teacher@test.com')).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith('/api/users/', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ type: 'TEACHER', name: 'Test Teacher', personalEmail: 'teacher@example.com', username: 'test.teacher' }),
    }));
  });

  it('keeps the form when the email could not be sent, so the admin can fix it', async () => {
    mockBackend({ status: 502, body: { code: 'EMAIL_NOT_SENT' } });
    renderPage();

    fillAndSubmit();

    expect(await screen.findByText("We couldn't send the email. Check the personal email and try again.")).toBeTruthy();
    expect((screen.getByLabelText('Personal email') as HTMLInputElement).value).toBe('teacher@example.com');
  });

  it('shows the backend message for an institutional email that is taken', async () => {
    mockBackend({ status: 409, body: { code: 'INSTITUTIONAL_EMAIL_ALREADY_EXISTS' } });
    renderPage();

    fillAndSubmit();

    expect(await screen.findByRole('alert')).toBeTruthy();
  });

  it('highlights a username the backend rejects', async () => {
    mockBackend({ status: 400, body: { code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'username', code: 'PATTERN' }] } });
    renderPage();

    fillAndSubmit();

    expect(await screen.findByText('This value has an invalid format.')).toBeTruthy();
  });
});
