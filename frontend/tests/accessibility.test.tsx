import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { Calendar, Menu, Rating, Switch, TagInput } from '../src/studymatch-ui';

afterEach(cleanup);
beforeEach(async () => { await i18n.changeLanguage('en'); });

// the controls SonarQube flagged, found by role and used without a mouse
describe('keyboard and screen reader access', () => {
  it('toggles a switch from its text, which labels it', () => {
    render(<Switch>Email notifications</Switch>);
    const toggle = screen.getByRole('switch', { name: 'Email notifications' });
    fireEvent.click(screen.getByText('Email notifications'));
    expect(toggle.getAttribute('aria-checked')).toBe('true');
  });

  it('moves the rating with the arrow keys', () => {
    const onValueChange = vi.fn();
    render(<Rating label="Tutor rating" defaultValue={1} onValueChange={onValueChange} />);
    const group = screen.getByRole('radiogroup', { name: 'Tutor rating' });
    fireEvent.keyDown(screen.getAllByRole('radio')[0], { key: 'ArrowRight' });
    expect(onValueChange).toHaveBeenCalledWith(2);
    expect(group.getAttribute('tabindex')).toBe('-1');
  });

  it('moves to the next day in the calendar with the arrow keys', async () => {
    render(<Calendar value={new Date(2026, 10, 18)} />);
    const grid = screen.getByRole('grid');
    const focusable = () => Array.from(grid.querySelectorAll('button')).find(day => day.tabIndex === 0);
    const before = focusable();
    fireEvent.keyDown(before!, { key: 'ArrowRight' });
    await waitFor(() => expect(focusable()).not.toBe(before));
    expect(grid.getAttribute('tabindex')).toBe('-1');
  });

  it('moves between menu items with the arrow keys', async () => {
    render(<Menu trigger={<button type="button">Options</button>} items={[{ label: 'Edit', onSelect: vi.fn() }, { label: 'Delete', onSelect: vi.fn() }]} />);
    fireEvent.click(screen.getByRole('button', { name: 'Options' }));
    const [edit, remove] = await screen.findAllByRole('menuitem');
    await waitFor(() => expect(document.activeElement).toBe(edit));
    fireEvent.keyDown(edit, { key: 'ArrowDown' });
    expect(document.activeElement).toBe(remove);
  });

  it('adds a tag with the keyboard from its text box', () => {
    render(<TagInput label="Skills" />);
    const input = screen.getByRole('textbox', { name: 'Skills' });
    fireEvent.change(input, { target: { value: 'Java' } });
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(screen.getByText('Java')).toBeTruthy();
  });
});
