import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getCategories, registerCategory } from '../src/api/categories';
import i18n from '../src/i18n';
import { StaffCategoriesPage } from '../src/pages/staff/StaffCategoriesPage';
import { StudyMatchProvider } from '../src/studymatch-ui';

vi.mock('../src/api/categories', () => ({
  getCategories: vi.fn(),
  registerCategory: vi.fn(),
}));

const categories = [{ id: 2, name: 'Test Programming' }, { id: 1, name: 'Test Maths' }];
const dialog = () => screen.getByRole('dialog', { name: 'New category' });

function renderPage() {
  render(<StudyMatchProvider><StaffCategoriesPage /></StudyMatchProvider>);
}

async function submitName(name: string) {
  fireEvent.click(await screen.findByRole('button', { name: 'New category' }));
  fireEvent.change(within(dialog()).getByLabelText('Name'), { target: { value: name } });
  fireEvent.click(within(dialog()).getByRole('button', { name: 'Register category' }));
}

const names = () => screen.getAllByRole('row').slice(1).map(row => row.textContent);

afterEach(() => {
  cleanup();
  vi.mocked(registerCategory).mockReset();
});
beforeEach(async () => {
  await i18n.changeLanguage('en');
  vi.mocked(getCategories).mockResolvedValue(categories);
});

describe('register category', () => {
  it('lists the categories by name', async () => {
    renderPage();
    await screen.findByText('Test Maths');
    expect(names()).toEqual(['Test Maths', 'Test Programming']);
  });

  it('shows an empty state when there are no categories yet', async () => {
    vi.mocked(getCategories).mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText('No categories yet')).toBeTruthy();
  });

  it('shows the error with a retry when the list does not load', async () => {
    vi.mocked(getCategories).mockRejectedValueOnce(new Error('offline'));
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('Test Maths')).toBeTruthy();
  });

  it('sends the trimmed name, then adds the category in order and confirms it', async () => {
    vi.mocked(registerCategory).mockResolvedValue({ id: 3, name: 'Test Networks' });
    renderPage();
    await submitName('  Test Networks ');

    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'New category' })).toBeNull());
    expect(registerCategory).toHaveBeenCalledWith({ name: 'Test Networks' });
    expect(names()).toEqual(['Test Maths', 'Test Networks', 'Test Programming']);
    expect(await screen.findByText('Category Test Networks registered.')).toBeTruthy();
  });

  it('keeps the form open with a warning when the name is taken', async () => {
    vi.mocked(registerCategory).mockRejectedValue({ code: 'CATEGORY_ALREADY_EXISTS' });
    renderPage();
    await submitName('Test Maths');

    expect(await within(dialog()).findByText('A category with this name already exists.')).toBeTruthy();
    expect(names()).toEqual(['Test Maths', 'Test Programming']);
  });

  it('marks the name field when it is left empty', async () => {
    vi.mocked(registerCategory).mockRejectedValue({
      code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'name', code: 'NOT_BLANK' }],
    });
    renderPage();
    await submitName('');

    expect(await within(dialog()).findByText('This field is required.')).toBeTruthy();
    expect(within(dialog()).getByLabelText('Name').getAttribute('aria-invalid')).toBe('true');
  });

  it('closes without registering when cancelled', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'New category' }));
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByRole('dialog', { name: 'New category' })).toBeNull();
    expect(registerCategory).not.toHaveBeenCalled();
  });
});
