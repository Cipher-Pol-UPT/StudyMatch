import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import i18n from '../src/i18n';
import { AuthLayout } from '../src/studymatch-ui';

const year = String(new Date().getFullYear());

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(cleanup);

describe('auth layout', () => {
  it('shows the StudyMatch headline and tagline by default', () => {
    render(<AuthLayout>Test page</AuthLayout>);

    expect(screen.getByRole('heading', { name: 'Better teams start here.' })).toBeTruthy();
    expect(screen.getByText(/StudyMatch forms balanced groups/)).toBeTruthy();
  });

  it('keeps the art and the footer when the brand is centred', () => {
    const { container } = render(<AuthLayout centerBrand>Test page</AuthLayout>);

    expect(container.querySelector('.sm-auth--center-brand')).toBeTruthy();
    expect(container.querySelector('.sm-auth__art')).toBeTruthy();
    expect(screen.getByText(year, { exact: false })).toBeTruthy();
  });

  it('hides the art and the footer separately', () => {
    const { container, rerender } = render(<AuthLayout hideArt>Test page</AuthLayout>);

    expect(container.querySelector('.sm-auth__art')).toBeNull();
    expect(screen.getByText(year, { exact: false })).toBeTruthy();

    rerender(<AuthLayout hideFooter>Test page</AuthLayout>);

    expect(container.querySelector('.sm-auth__art')).toBeTruthy();
    expect(screen.queryByText(year, { exact: false })).toBeNull();
  });
});
