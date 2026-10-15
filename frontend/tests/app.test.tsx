import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import App from '../src/App';
import i18n from '../src/i18n';
import { StudyMatchProvider } from '../src/studymatch-ui';

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(cleanup);

describe('app start', () => {
  it('shows the loading screen before the log in page', async () => {
    render(<StudyMatchProvider><App /></StudyMatchProvider>);

    expect(screen.getByRole('status', { name: 'Loading' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Log in' })).toBeNull();

    expect(await screen.findByRole('button', { name: 'Log in' }, { timeout: 3000 })).toBeTruthy();
  });
});
