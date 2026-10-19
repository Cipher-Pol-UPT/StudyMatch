import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import i18n from '../src/i18n';
import { StudyRoomsPage } from '../src/pages/studyRooms/StudyRoomsPage';
import { StudyMatchProvider } from '../src/studymatch-ui';

const user = { number: 45123, name: 'Manu Yepes' };
const initialDate = new Date(2026, 9, 19);
const details = () => screen.getByRole('dialog', { name: 'Selected study room' });
const event = (topic: string) => screen.getByRole('button', { name: new RegExp(`^View ${topic},`) });

const waitForClosedPopup = () => waitFor(() => expect(screen.queryByRole('dialog')).toBeNull(), { timeout: 3000 });

async function closePopup() {
  fireEvent.click(within(details()).getByRole('button', { name: 'Close', exact: true }));
  await waitForClosedPopup();
}

function renderPage() {
  render(<StudyMatchProvider><StudyRoomsPage currentUser={user} initialDate={initialDate} /></StudyMatchProvider>);
}

afterEach(cleanup);
beforeEach(async () => { await i18n.changeLanguage('en'); });

describe('study rooms demo', () => {
  it('starts closed and opens the clicked session in a modal with its tutor, time and available places', async () => {
    renderPage();
    expect(screen.queryByRole('dialog')).toBeNull();
    fireEvent.click(event('Clean code, clear thinking'));
    expect(within(details()).getByRole('heading', { name: 'Clean code, clear thinking' })).toBeTruthy();
    expect(within(details()).getByText('Tiago Costa')).toBeTruthy();
    expect(within(details()).getByText(/17:30–18:30/)).toBeTruthy();
    expect(within(details()).getByText('5/12 · 7 left')).toBeTruthy();
    expect(event('Clean code, clear thinking').getAttribute('aria-pressed')).toBe('true');
    expect(details().getAttribute('aria-modal')).toBe('true');
    expect(document.body.style.overflow).toBe('hidden');
    await closePopup();
    expect(document.body.style.overflow).toBe('');
    expect(document.activeElement).toBe(event('Clean code, clear thinking'));
  });

  it('closes the popup with Escape or a click on the backdrop', async () => {
    renderPage();
    fireEvent.click(event('Clean code, clear thinking'));
    fireEvent.keyDown(document, { key: 'Escape' });
    await waitForClosedPopup();
    expect(document.activeElement).toBe(event('Clean code, clear thinking'));
    fireEvent.click(event('Hypothesis tests without fear'));
    fireEvent.click(document.querySelector('.sm-scrim')!);
    await waitForClosedPopup();
    expect(document.body.style.overflow).toBe('');
  });

  it('shows only the clicked subject, keeps it selected on a second click, and can restore all subjects', () => {
    renderPage();
    const statistics = screen.getByRole('button', { name: 'Statistics', exact: true });
    fireEvent.click(statistics);
    expect(screen.getAllByRole('button', { name: /^View / })).toHaveLength(4);
    expect(event('Hypothesis tests without fear')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^View Clean code/ })).toBeNull();
    expect(statistics.getAttribute('aria-pressed')).toBe('true');
    fireEvent.click(statistics);
    expect(screen.getAllByRole('button', { name: /^View / })).toHaveLength(4);
    fireEvent.click(screen.getByRole('button', { name: 'Programming Lab', exact: true }));
    expect(event('Clean code, clear thinking')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^View Hypothesis tests/ })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Show all subjects' }));
    expect(screen.getAllByRole('button', { name: /^View / })).toHaveLength(18);
  });

  it('shows only enrolled rooms in inscriptions and only hosted rooms in my study rooms', () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /^My inscriptions/ }));
    expect(screen.getAllByRole('article')).toHaveLength(6);
    expect(screen.getByRole('article', { name: 'Hypothesis tests without fear' })).toBeTruthy();
    expect(screen.queryByRole('article', { name: 'Software quality essentials' })).toBeNull();
    expect(screen.queryByRole('article', { name: 'Clean code, clear thinking' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Statistics', exact: true }));
    expect(screen.getAllByRole('article')).toHaveLength(3);
    expect(screen.getByRole('article', { name: 'Hypothesis tests without fear' })).toBeTruthy();
    expect(screen.queryByRole('article', { name: 'A better code review' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /^My study rooms/ }));
    expect(screen.queryByRole('article')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Show all subjects' }));
    expect(screen.getAllByRole('article')).toHaveLength(3);
    expect(screen.getByRole('article', { name: 'Software quality essentials' })).toBeTruthy();
    expect(screen.getAllByText(user.name)).toHaveLength(3);
    expect(screen.queryByRole('button', { name: 'Join' })).toBeNull();
  });

  it('keeps join and leave changes consistent across the popup, capacity and inscriptions', async () => {
    renderPage();
    fireEvent.click(event('Clean code, clear thinking'));
    fireEvent.click(within(details()).getByRole('button', { name: 'Join' }));
    expect(within(details()).getByText('6/12 · 6 left')).toBeTruthy();
    await closePopup();
    fireEvent.click(screen.getByRole('button', { name: /^My inscriptions/ }));
    expect(screen.getAllByRole('article')).toHaveLength(7);
    const room = screen.getByRole('article', { name: 'Clean code, clear thinking' });
    fireEvent.click(within(room).getByRole('button', { name: 'Leave' }));
    expect(screen.queryByRole('article', { name: 'Clean code, clear thinking' })).toBeNull();
    expect(screen.getAllByRole('article')).toHaveLength(6);
    fireEvent.click(screen.getByRole('button', { name: /^Calendar/ }));
    fireEvent.click(event('Clean code, clear thinking'));
    expect(within(details()).getByText('5/12 · 7 left')).toBeTruthy();
    expect(within(details()).getByRole('button', { name: 'Join' })).toBeTruthy();
  });

  it('disables joining a full room', () => {
    renderPage();
    fireEvent.click(event('Neural networks, one step at a time'));
    expect((within(details()).getByRole('button', { name: 'Full' }) as HTMLButtonElement).disabled).toBe(true);
    expect(within(details()).getByText('8/8 · 0 left')).toBeTruthy();
  });

  it('keeps both calendars synchronized and lets a mini-calendar date open a room', () => {
    renderPage();
    const picker = screen.getByRole('region', { name: 'Month picker' });
    fireEvent.click(within(picker).getByRole('button', { name: 'Next month' }));
    expect(screen.getByRole('heading', { name: 'November 2026' })).toBeTruthy();
    expect(screen.getByText('No study rooms to show')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Today' }));
    expect(screen.getByRole('heading', { name: 'October 2026' })).toBeTruthy();
    fireEvent.click(within(picker).getByRole('button', { name: 'Monday, October 5, 2026' }));
    expect(within(details()).getByRole('article', { name: 'Clean code, clear thinking' })).toBeTruthy();
  });

  it('shows an empty personal list for an unmatched subject and resets the filter from browse', () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Statistics', exact: true }));
    fireEvent.click(screen.getByRole('button', { name: /^My study rooms/ }));
    expect(screen.getByText('No study rooms to show')).toBeTruthy();
    expect(screen.queryByRole('article')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Browse study rooms' }));
    expect(screen.getAllByRole('button', { name: /^View / })).toHaveLength(18);
  });

  it('uses the app language for filters, dates, and personal views', async () => {
    await i18n.changeLanguage('pt');
    renderPage();
    expect(screen.getByRole('button', { name: 'Estatística', exact: true })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: /^seg/i })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /^As minhas salas de estudo/ }));
    expect(screen.getByRole('heading', { name: 'As minhas salas de estudo' })).toBeTruthy();
    expect(screen.getAllByRole('article')).toHaveLength(3);
  });
});
