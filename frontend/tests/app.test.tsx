import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../src/App';
import i18n from '../src/i18n';
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

function mockCurrentUserResponse(status: number, body: unknown) {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(body), { status })));
}

function renderApp() {
  render(<StudyMatchProvider><App /></StudyMatchProvider>);
}

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('app start', () => {
  it('shows the login page when there is no session', async () => {
    mockCurrentUserResponse(401, { code: 'UNAUTHENTICATED' });
    renderApp();

    expect(await screen.findByRole('button', { name: 'Log in' })).toBeTruthy();
  });

  it('restores the session of a logged in user', async () => {
    mockCurrentUserResponse(200, student);
    renderApp();

    expect(await screen.findByText('Hello, Maria Costa.')).toBeTruthy();
  });

  it('sends a user with a one-time password to change it first', async () => {
    mockCurrentUserResponse(200, { ...student, mustChangePassword: true, personalityTestRequired: true });
    renderApp();

    expect(await screen.findByText('Change password')).toBeTruthy();
  });

  it('sends a student without a personality test to take it', async () => {
    mockCurrentUserResponse(200, { ...student, personalityTestRequired: true });
    renderApp();

    expect(await screen.findByText('Personality test')).toBeTruthy();
  });
});

describe('my profile', () => {
  afterEach(() => {
    window.history.pushState({}, '', '/');
  });

  it('opens the profile page for a student on /profile', async () => {
    window.history.pushState({}, '', '/profile');
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const body = url === '/api/users/me' ? student : {
        number: 50001, name: 'Maria Costa', banner: null, pfp: null, aboutMe: null, skills: [], interests: [],
        languages: ['PORTUGUESE'], tutorBadges: [], achievements: [], privateProfile: null,
      };
      return new Response(JSON.stringify(body), { status: 200 });
    }));
    renderApp();

    expect(await screen.findByRole('button', { name: 'Edit profile' })).toBeTruthy();
  });
});
