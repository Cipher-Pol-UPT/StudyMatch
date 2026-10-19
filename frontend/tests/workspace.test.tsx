import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { WorkspaceArea } from '../src/pages/workspace/WorkspaceArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { CurrentUser } from '../src/types/currentUser';
import type { Role } from '../src/types/enums';

function userWith(role: Role): CurrentUser {
  return {
    number: 10, name: 'Test User', institutionalEmail: 'test.user@test.com', role,
    mustChangePassword: false, personalityTestRequired: false,
  };
}

function renderArea(role: Role, path = '/') {
  const onLoggedOut = vi.fn();
  render(
    <StudyMatchProvider>
      <MemoryRouter initialEntries={[path]}>
        <WorkspaceArea currentUser={userWith(role)} onLoggedOut={onLoggedOut} />
      </MemoryRouter>
    </StudyMatchProvider>,
  );
  return onLoggedOut;
}

beforeEach(async () => {
  await i18n.changeLanguage('en');
  document.cookie = 'XSRF-TOKEN=test-csrf-token';
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify([]), { status: 200 })));
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('staff, admin and teacher area', () => {
  it('gives staff the student registration', () => {
    renderArea('STAFF');

    expect(screen.getByRole('heading', { name: 'Hello, Test User.' })).toBeTruthy();
    expect(screen.getAllByRole('link', { name: /Register students/ }).length).toBeGreaterThan(0);
    expect(screen.queryByRole('link', { name: /Create account/ })).toBeNull();
  });

  it('gives the admin the account creation', () => {
    renderArea('ADMIN');

    expect(screen.getAllByRole('link', { name: /Create account/ }).length).toBeGreaterThan(0);
    expect(screen.queryByRole('link', { name: /Register students/ })).toBeNull();
  });

  it('shows a teacher a home page without tools yet', () => {
    renderArea('TEACHER');

    expect(screen.getByText('Your tools will appear here')).toBeTruthy();
  });

  it('does not open a page of another role', () => {
    renderArea('STAFF', '/accounts/new');

    expect(screen.getByRole('heading', { name: 'Hello, Test User.' })).toBeTruthy();
  });

  it('logs out from the account menu', async () => {
    const onLoggedOut = renderArea('ADMIN');

    fireEvent.click(screen.getByRole('button', { name: 'Account menu' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Log out' }));

    await waitFor(() => expect(onLoggedOut).toHaveBeenCalled());
  });
});
