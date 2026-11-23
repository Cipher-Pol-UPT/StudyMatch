import { cleanup, configure, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { StudentArea } from '../src/pages/student/StudentArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { ChatContactData } from '../src/types/chat';
import type { CurrentUser } from '../src/types/currentUser';

// these render a whole area, which takes longer than the default second when every test file runs at once
configure({ asyncUtilTimeout: 5000 });
vi.setConfig({ testTimeout: 15000 });

const me: CurrentUser = { number: 50002, name: 'Test Student', institutionalEmail: 'student@school.pt', role: 'STUDENT', mustChangePassword: false, personalityTestRequired: false };
const ana: ChatContactData = { number: 50001, name: 'Test Ana', role: 'STUDENT' };
const rui: ChatContactData = { number: 9001, name: 'Test Rui', role: 'TEACHER' };
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

// a classmate's profile as the backend gives it to another student: no private part
const anaProfile = {
  number: ana.number, name: ana.name, banner: null, pfp: null, aboutMe: 'Likes databases.', skills: ['SQL'], interests: [],
  languages: ['PORTUGUESE'], tutorBadges: [], achievements: [], privateProfile: null,
};

function backend() {
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async input => {
    const url = new URL(String(input), 'https://test.invalid');
    if (url.pathname === `/api/students/${ana.number}/profile`) return reply(anaProfile);
    if (url.pathname.match(/^\/api\/students\/\d+\/profile$/)) return reply({ code: 'NOT_FOUND' }, 404);
    // the student area's own profile, for the menu and My profile
    if (url.pathname === '/api/students/me/profile') return reply({ ...anaProfile, number: me.number, name: me.name });
    const dm = url.pathname.match(/^\/api\/chats\/direct\/(\d+)$/);
    if (dm) return reply({ chatId: null, contact: Number(dm[1]) === ana.number ? ana : rui });
    throw new Error(`Unexpected request: ${url.pathname}`);
  }));
}

function open(path: string) {
  render(<StudyMatchProvider><MemoryRouter initialEntries={[path]}><StudentArea currentUser={me} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

beforeEach(async () => { await i18n.changeLanguage('en'); backend(); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe("a classmate's profile", () => {
  it('shows only the public part, with a Message button to their DM', async () => {
    open(`/classmates/${ana.number}`);

    expect(await screen.findByRole('heading', { level: 1, name: 'Test Ana' })).toBeTruthy();
    expect(screen.getByText('Likes databases.')).toBeTruthy();
    expect(screen.getByText('SQL')).toBeTruthy();
    expect(screen.queryByText(/Year/)).toBeNull();
    expect(screen.queryByText(i18n.t('ui.personality_profile'))).toBeNull();
    expect(screen.getByRole('link', { name: 'Message' }).getAttribute('href')).toBe(`/chats/direct/${ana.number}`);
    expect(screen.queryByRole('button', { name: i18n.t('ui.edit_profile') })).toBeNull();
  });

  it("opens from the head of a DM with a student", async () => {
    open(`/chats/direct/${ana.number}`);

    fireEvent.click(await screen.findByRole('link', { name: "Open Test Ana's profile" }));

    expect(await screen.findByText('Likes databases.')).toBeTruthy();
  });

  it("isn't a link in a DM with a teacher, who has no profile", async () => {
    open(`/chats/direct/${rui.number}`);

    const conversation = within(await screen.findByRole('region', { name: 'Conversation with Test Rui' }));

    expect(conversation.getByRole('heading', { level: 2, name: 'Test Rui' })).toBeTruthy();
    expect(screen.queryByRole('link', { name: "Open Test Rui's profile" })).toBeNull();
  });

  it('opens My profile for your own number', async () => {
    open(`/classmates/${me.number}`);

    expect(await screen.findByRole('button', { name: i18n.t('ui.edit_profile') })).toBeTruthy();
    expect(vi.mocked(fetch).mock.calls.some(([input]) => String(input).includes(`/students/${me.number}/profile`))).toBe(false);
  });

  it("says so when there's no such student", async () => {
    open('/classmates/123');

    expect(await screen.findByText(i18n.t('errors.NOT_FOUND'))).toBeTruthy();
  });

  it("says the same for something that isn't a student number, without asking the backend", async () => {
    open('/classmates/not-a-number');

    expect(await screen.findByText(i18n.t('errors.NOT_FOUND'))).toBeTruthy();
    expect(vi.mocked(fetch).mock.calls.some(([input]) => String(input).includes('/not-a-number'))).toBe(false);
  });
});
