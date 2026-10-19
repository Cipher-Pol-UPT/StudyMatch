import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { ProfilePage } from '../src/pages/profile/ProfilePage';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { StudentProfileData } from '../src/types/studentProfile';

const BANNER = 'data:image/png;base64,iVBORw0KGgo=';

const PFP = 'data:image/jpeg;base64,/9j/4AAQ';

const profile: StudentProfileData = {
  number: 50001,
  name: 'Test Student',
  banner: BANNER,
  pfp: PFP,
  aboutMe: 'Test about me',
  skills: ['Test Skill'],
  interests: ['Test Interest'],
  languages: ['PORTUGUESE'],
  tutorBadges: [],
  achievements: [],
  privateProfile: {
    institutionalEmail: '50001@students.test.com',
    degree: 'Test Degree',
    curricularYear: 2,
    personalityProfile: { leadership: 0.8, problemSolving: 0.6, creativity: 0.4, execution: 0.7 },
    attendance: 75,
    hasAcademicHistory: true,
  },
};

interface BackendReply {
  status: number;
  body: unknown;
}

function mockBackend(replies: { load?: BackendReply[]; save?: BackendReply }) {
  const loads = [...(replies.load ?? [{ status: 200, body: profile }])];
  const fetchMock = vi.fn<typeof fetch>(async (_url, init) => {
    const reply = init?.method === 'PUT' ? replies.save! : loads.shift() ?? { status: 200, body: profile };
    return new Response(JSON.stringify(reply.body), { status: reply.status });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderProfilePage() {
  render(<StudyMatchProvider><ProfilePage /></StudyMatchProvider>);
}

async function openEditProfile() {
  fireEvent.click(await screen.findByRole('button', { name: 'Edit profile' }));
}

beforeEach(async () => {
  await i18n.changeLanguage('en');
  document.cookie = 'XSRF-TOKEN=test-csrf-token';
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('profile view', () => {
  it('shows the student their own profile from the backend', async () => {
    const fetchMock = mockBackend({});
    renderProfilePage();

    expect(await screen.findByRole('heading', { name: 'Test Student' })).toBeTruthy();
    expect(screen.getByText('Test about me')).toBeTruthy();
    expect(screen.getByText('50001@students.test.com', { exact: false })).toBeTruthy();
    expect(screen.getByText('75%')).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith('/api/students/me/profile', expect.objectContaining({ method: 'GET' }));
  });

  it('shows the profile photo instead of the initials', async () => {
    mockBackend({});
    renderProfilePage();

    await screen.findByRole('heading', { name: 'Test Student' });

    expect(document.querySelector(`img[src="${PFP}"]`)).toBeTruthy();
  });

  it('says there is no academic history yet', async () => {
    mockBackend({
      load: [{ status: 200, body: { ...profile, privateProfile: { ...profile.privateProfile, attendance: null, hasAcademicHistory: false } } }],
    });
    renderProfilePage();

    expect(await screen.findByText('No academic history yet')).toBeTruthy();
  });

  it('lets the student try again when the profile cannot be loaded', async () => {
    mockBackend({ load: [{ status: 500, body: {} }, { status: 200, body: profile }] });
    renderProfilePage();

    fireEvent.click(await screen.findByRole('button', { name: 'Try again' }));

    expect(await screen.findByRole('heading', { name: 'Test Student' })).toBeTruthy();
  });
});

describe('edit profile', () => {
  it('saves the changes with the current banner and profile photo and shows a confirmation', async () => {
    const saved = { ...profile, aboutMe: 'New test about me' };
    const fetchMock = mockBackend({ save: { status: 200, body: saved } });
    renderProfilePage();

    await openEditProfile();
    fireEvent.change(screen.getByLabelText('About me'), { target: { value: 'New test about me' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Profile updated.')).toBeTruthy();
    expect(screen.getByText('New test about me')).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith('/api/students/me/profile', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'test-csrf-token' },
      credentials: 'include',
      body: JSON.stringify({
        banner: BANNER,
        pfp: PFP,
        aboutMe: 'New test about me',
        skills: ['Test Skill'],
        interests: ['Test Interest'],
        languages: ['PORTUGUESE'],
      }),
    });
  });

  it('shows the backend message when no language is selected', async () => {
    mockBackend({ save: { status: 400, body: { code: 'AT_LEAST_ONE_LANGUAGE_REQUIRED' } } });
    renderProfilePage();

    await openEditProfile();
    fireEvent.click(screen.getByRole('button', { name: 'Portuguese' }));
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Select at least one language.')).toBeTruthy();
  });

  it('shows the backend message for an invalid banner', async () => {
    mockBackend({ save: { status: 400, body: { code: 'INVALID_BANNER' } } });
    renderProfilePage();

    await openEditProfile();
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Use a JPG or PNG image up to 5 MB.')).toBeTruthy();
  });

  it('shows the backend message for an invalid profile photo', async () => {
    mockBackend({ save: { status: 400, body: { code: 'INVALID_PROFILE_PHOTO' } } });
    renderProfilePage();

    await openEditProfile();
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Use a JPG or PNG image up to 1 MB.')).toBeTruthy();
  });

  it('shows a field error the backend reports for a skill under the skills field', async () => {
    mockBackend({
      save: { status: 400, body: { code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'skills[]', code: 'NOT_BLANK' }] } },
    });
    renderProfilePage();

    await openEditProfile();
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Check the highlighted fields.')).toBeTruthy();
    expect(screen.getByText('This field is required.')).toBeTruthy();
  });

  it('discards the changes on cancel', async () => {
    const fetchMock = mockBackend({});
    renderProfilePage();

    await openEditProfile();
    fireEvent.change(screen.getByLabelText('About me'), { target: { value: 'Unsaved about me' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    await waitFor(() => expect(screen.getByText('Test about me')).toBeTruthy());
    expect(screen.queryByText('Unsaved about me')).toBeNull();
    expect(fetchMock).not.toHaveBeenCalledWith('/api/students/me/profile', expect.objectContaining({ method: 'PUT' }));
  });
});
