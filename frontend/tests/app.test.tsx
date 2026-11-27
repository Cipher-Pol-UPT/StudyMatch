import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../src/App';
import i18n from '../src/i18n';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { CurrentUser } from '../src/types/currentUser';
import { connectChatSocket } from '../src/api/chatSocket';

vi.mock('../src/api/chatSocket', () => ({ onChatSocketReconnect: () => () => undefined,
  // the connection is down, so nobody shows as online
  isChatSocketConnected: () => false, onChatSocketConnection: () => () => undefined, sendTyping: vi.fn(), connectChatSocket: vi.fn(), disconnectChatSocket: vi.fn(async () => {}), subscribeToChatEvents: vi.fn(() => () => undefined) }));

const student: CurrentUser = {
  number: 50001,
  name: 'Maria Costa',
  institutionalEmail: '50001@students.studymatch.com',
  role: 'STUDENT',
  mustChangePassword: false,
  personalityTestRequired: false,
};

// the home page's sticky notes ask for /notes/ too, which answers an empty board
function mockCurrentUserResponse(status: number, body: unknown) {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => String(url).startsWith('/api/notes/')
    ? new Response('[]', { status: 200 })
    : new Response(JSON.stringify(body), { status })));
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
  vi.clearAllMocks();
});

describe('app start', () => {
  it('shows the loading screen until the session is known', async () => {
    mockCurrentUserResponse(401, { code: 'UNAUTHENTICATED' });
    renderApp();

    expect(screen.getByRole('status', { name: 'Loading' })).toBeTruthy();
    expect(await screen.findByRole('button', { name: 'Log in' })).toBeTruthy();
    await waitFor(() => expect(screen.queryByRole('status', { name: 'Loading' })).toBeNull());
  });

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

    expect(await screen.findByRole('heading', { name: 'Choose your password' })).toBeTruthy();
  });

  it('sends a student without a personality test to take it', async () => {
    mockCurrentUserResponse(200, { ...student, personalityTestRequired: true });
    renderApp();

    expect(await screen.findByText('Personality test')).toBeTruthy();
    expect(connectChatSocket).not.toHaveBeenCalled();
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
