import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getCategories, renameCategory } from '../src/api/categories';
import i18n from '../src/i18n';
import { StaffCategoriesPage } from '../src/pages/staff/StaffCategoriesPage';
import { StudyMatchProvider } from '../src/studymatch-ui';

vi.mock('../src/api/categories', () => ({
  getCategories: vi.fn(),
  registerCategory: vi.fn(),
  renameCategory: vi.fn(),
}));

const categories = [{ id: 2, name: 'Test Programming' }, { id: 1, name: 'Test Maths' }];
const dialog = () => screen.getByRole('dialog', { name: 'Rename category' });
const names = () => screen.getAllByRole('row').slice(1).map(row => row.querySelector('td')?.textContent);

function renderPage() {
  render(<StudyMatchProvider><StaffCategoriesPage /></StudyMatchProvider>);
}

async function rename(from: string, to: string) {
  fireEvent.click(await screen.findByRole('button', { name: `Rename ${from}` }));
  fireEvent.change(within(dialog()).getByLabelText('Name'), { target: { value: to } });
  fireEvent.click(within(dialog()).getByRole('button', { name: 'Save' }));
}

afterEach(() => {
  cleanup();
  vi.mocked(renameCategory).mockReset();
});
beforeEach(async () => {
  await i18n.changeLanguage('en');
  vi.mocked(getCategories).mockResolvedValue(categories);
});

describe('rename category', () => {
  it('opens with the current name filled in', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Rename Test Maths' }));
    expect((within(dialog()).getByLabelText('Name') as HTMLInputElement).value).toBe('Test Maths');
  });

  it('sends the trimmed name, then shows the new name in order and confirms it', async () => {
    vi.mocked(renameCategory).mockResolvedValue({ id: 1, name: 'Test Statistics' });
    renderPage();
    await rename('Test Maths', '  Test Statistics ');

    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Rename category' })).toBeNull());
    expect(renameCategory).toHaveBeenCalledWith(1, { name: 'Test Statistics' });
    expect(names()).toEqual(['Test Programming', 'Test Statistics']);
    expect(await screen.findByText('Category renamed to Test Statistics.')).toBeTruthy();
  });

  it('shows a taken name under the name field and keeps the dialog open', async () => {
    vi.mocked(renameCategory).mockRejectedValue({ code: 'CATEGORY_ALREADY_EXISTS' });
    renderPage();
    await rename('Test Maths', 'Test Programming');

    expect(await within(dialog()).findByText('A category with this name already exists.')).toBeTruthy();
    expect(within(dialog()).getByLabelText('Name').getAttribute('aria-invalid')).toBe('true');
    expect(dialog().querySelector('.sm-banner')).toBeNull();
    expect(names()).toEqual(['Test Maths', 'Test Programming']);
  });

  it('marks the name field when it is left empty', async () => {
    vi.mocked(renameCategory).mockRejectedValue({
      code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'name', code: 'NOT_BLANK' }],
    });
    renderPage();
    await rename('Test Maths', '');

    expect(await within(dialog()).findByText('This field is required.')).toBeTruthy();
  });

  it('closes without renaming when cancelled', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Rename Test Maths' }));
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Cancel' }));

    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Rename category' })).toBeNull());
    expect(renameCategory).not.toHaveBeenCalled();
    expect(names()).toEqual(['Test Maths', 'Test Programming']);
  });
});
