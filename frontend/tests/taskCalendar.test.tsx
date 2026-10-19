import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { TaskCalendar } from '../src/pages/home/TaskCalendar';
import type { CalendarTask } from '../src/pages/home/types';

const TODAY = new Date(2026, 9, 21);

afterEach(cleanup);
beforeEach(async () => { await i18n.changeLanguage('en'); });

describe('TaskCalendar', () => {
  it('groups tasks by day and collapses the overflow into "+N more"', () => {
    const tasks: CalendarTask[] = ['T1', 'T2', 'T3', 'T4', 'T5'].map((title) => ({ id: title, title, date: '2026-10-22' }));
    render(<TaskCalendar tasks={tasks} today={TODAY} />);
    const cell = screen.getByRole('cell', { name: /22/ });
    ['T1', 'T2', 'T3'].forEach((title) => expect(within(cell).getByText(title)).toBeTruthy());
    expect(within(cell).queryByText('T4')).toBeNull();
    expect(within(cell).getByText('+2 more')).toBeTruthy();
  });

  it('moves one week at a time and returns with Today', () => {
    const tasks: CalendarTask[] = [{ id: 'later', title: 'Later task', date: '2026-11-16' }];
    render(<TaskCalendar tasks={tasks} today={TODAY} weeks={4} />);
    const today = screen.getByRole('button', { name: 'Today' }) as HTMLButtonElement;
    expect(screen.queryByText('Later task')).toBeNull();
    expect(today.disabled).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: 'Next week' }));
    expect(screen.getByText('Later task')).toBeTruthy();
    expect(today.disabled).toBe(false);
    fireEvent.click(today);
    expect(screen.queryByText('Later task')).toBeNull();
  });

  it('names the weekdays in the app language', async () => {
    const { unmount } = render(<TaskCalendar tasks={[]} today={TODAY} />);
    expect(screen.getByRole('columnheader', { name: 'Mon' })).toBeTruthy();
    unmount();
    await i18n.changeLanguage('pt');
    render(<TaskCalendar tasks={[]} today={TODAY} />);
    expect(screen.getByRole('columnheader', { name: /^seg/i })).toBeTruthy();
  });

  it('reports the clicked task', () => {
    const task: CalendarTask = { id: 'a', title: 'Essay', date: '2026-10-20' };
    const onTaskClick = vi.fn();
    render(<TaskCalendar tasks={[task]} today={TODAY} onTaskClick={onTaskClick} />);
    fireEvent.click(screen.getByRole('button', { name: 'Essay' }));
    expect(onTaskClick).toHaveBeenCalledWith(task);
  });
});