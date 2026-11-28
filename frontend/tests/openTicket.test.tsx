import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getMyTickets, getTicket, openTicket, replyToTicket } from '../src/api/tickets';
import App from '../src/App';
import i18n from '../src/i18n';
import { MyTicketsPage } from '../src/pages/help/MyTicketsPage';
import { TicketPage } from '../src/pages/help/TicketPage';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { TicketData, TicketSummaryData } from '../src/types/tickets';

vi.mock('../src/api/tickets', () => ({
  openTicket: vi.fn(),
  getMyTickets: vi.fn(),
  getTicket: vi.fn(),
  replyToTicket: vi.fn(),
}));
vi.mock('../src/api/chatSocket', () => ({ connectChatSocket: vi.fn(), disconnectChatSocket: vi.fn(async () => {}) }));

const ME = 50001;
const grades: TicketSummaryData = { id: 7, category: 'GRADES_AND_ENROLMENT', subject: 'My Databases grade is missing', status: 'IN_PROGRESS',
  createdAt: '2026-11-20T10:00:00', updatedAt: '2026-11-21T09:30:00' };
const login: TicketSummaryData = { id: 3, category: 'TECHNICAL', subject: 'The calendar does not load', status: 'RESOLVED',
  createdAt: '2026-11-02T10:00:00', updatedAt: '2026-11-03T12:00:00' };
const thread: TicketData = { ...grades, description: 'The grade for the project is not on my page.', replies: [
  { id: 1, authorNumber: 9001, authorName: 'Sofia Staff', authorRole: 'STAFF', text: 'We are checking it with the teacher.', createdAt: '2026-11-21T09:30:00' },
] };

function renderAt(path: string) {
  render(
    <StudyMatchProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/help" element={<MyTicketsPage />} />
          <Route path="/help/:ticketId" element={<TicketPage me={ME} myName="Maria Costa" />} />
        </Routes>
      </MemoryRouter>
    </StudyMatchProvider>,
  );
}

const dialog = () => screen.getByRole('dialog', { name: 'New ticket' });

beforeEach(async () => {
  await i18n.changeLanguage('en');
  vi.mocked(getMyTickets).mockResolvedValue([grades, login]);
  vi.mocked(getTicket).mockResolvedValue(thread);
});
afterEach(() => {
  cleanup();
  vi.mocked(openTicket).mockReset();
  vi.mocked(replyToTicket).mockReset();
  vi.unstubAllGlobals();
  window.history.pushState({}, '', '/');
});

describe('open a ticket', () => {
  it('lists my tickets with their subject, category, status and last update', async () => {
    renderAt('/help');

    const rows = (await screen.findAllByRole('row')).slice(1);
    expect(rows.map(row => row.querySelector('td')?.textContent)).toEqual(['My Databases grade is missing', 'The calendar does not load']);
    expect(within(rows[0]).getByText('Grades and enrolment')).toBeTruthy();
    expect(within(rows[0]).getByText('In progress')).toBeTruthy();
    expect(within(rows[1]).getByText('Resolved')).toBeTruthy();
  });

  it('with none it says so and offers New ticket', async () => {
    vi.mocked(getMyTickets).mockResolvedValue([]);
    renderAt('/help');
    expect(await screen.findByText('No tickets yet')).toBeTruthy();
    expect(screen.getAllByRole('button', { name: 'New ticket' })).toHaveLength(2);
  });

  it('opens a ticket with the text trimmed, and it goes to the top of the list', async () => {
    vi.mocked(openTicket).mockResolvedValue({ id: 9, category: 'ACCOUNT', subject: 'I cannot change my email', status: 'OPEN',
      createdAt: '2026-11-28T10:00:00', updatedAt: '2026-11-28T10:00:00' });
    renderAt('/help');
    fireEvent.click(await screen.findByRole('button', { name: 'New ticket' }));
    const send = within(dialog()).getByRole('button', { name: 'Send ticket' });
    expect(send.hasAttribute('disabled')).toBe(true);

    fireEvent.change(within(dialog()).getByLabelText('Category'), { target: { value: 'ACCOUNT' } });
    expect(within(dialog()).getByText('The administrators answer account questions.')).toBeTruthy();
    fireEvent.change(within(dialog()).getByLabelText('Subject'), { target: { value: ' I cannot change my email ' } });
    fireEvent.change(within(dialog()).getByLabelText('Description'), { target: { value: 'It says the address is taken.\n' } });
    fireEvent.click(send);

    await waitFor(() => expect(openTicket).toHaveBeenCalledWith({ category: 'ACCOUNT', subject: 'I cannot change my email', description: 'It says the address is taken.' }));
    expect(await screen.findByText("Ticket sent. You'll see the answers here.")).toBeTruthy();
    expect(screen.getAllByRole('row')[1].textContent).toContain('I cannot change my email');
  });

  it("shows the backend's validation error under its field", async () => {
    vi.mocked(openTicket).mockRejectedValue({ code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'subject', code: 'SIZE' }] });
    renderAt('/help');
    fireEvent.click(await screen.findByRole('button', { name: 'New ticket' }));
    fireEvent.change(within(dialog()).getByLabelText('Category'), { target: { value: 'OTHER' } });
    fireEvent.change(within(dialog()).getByLabelText('Subject'), { target: { value: 'Question' } });
    fireEvent.change(within(dialog()).getByLabelText('Description'), { target: { value: 'Something' } });
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Send ticket' }));

    await waitFor(() => expect(within(dialog()).getByLabelText('Subject').getAttribute('aria-invalid')).toBe('true'));
  });

  it('opening one shows what I wrote, then the answers with who wrote them', async () => {
    renderAt('/help');
    fireEvent.click(await screen.findByText('My Databases grade is missing'));

    const messages = within(await screen.findByRole('list', { name: 'Conversation' })).getAllByRole('listitem');
    expect(messages).toHaveLength(2);
    expect(within(messages[0]).getByText('You')).toBeTruthy();
    expect(within(messages[0]).getByText('The grade for the project is not on my page.')).toBeTruthy();
    expect(within(messages[1]).getByText('Sofia Staff')).toBeTruthy();
    expect(within(messages[1]).getByText('Staff')).toBeTruthy();
    expect(getTicket).toHaveBeenCalledWith(7);
  });

  it('replies and shows the thread the backend answers with', async () => {
    vi.mocked(replyToTicket).mockResolvedValue({ ...thread, replies: [...thread.replies,
      { id: 2, authorNumber: ME, authorName: 'Maria Costa', authorRole: 'STUDENT', text: 'Thank you!', createdAt: '2026-11-21T10:00:00' }] });
    renderAt('/help/7');
    fireEvent.change(await screen.findByLabelText('Reply'), { target: { value: ' Thank you! ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send reply' }));

    await waitFor(() => expect(replyToTicket).toHaveBeenCalledWith(7, 'Thank you!'));
    const messages = within(screen.getByRole('list', { name: 'Conversation' })).getAllByRole('listitem');
    expect(messages).toHaveLength(3);
    expect(within(messages[2]).getByText('Thank you!')).toBeTruthy();
    expect((screen.getByLabelText('Reply') as HTMLTextAreaElement).value).toBe('');
  });

  it('a resolved ticket says replying reopens it, and shows it open again after', async () => {
    vi.mocked(getTicket).mockResolvedValue({ ...thread, status: 'RESOLVED' });
    vi.mocked(replyToTicket).mockResolvedValue({ ...thread, status: 'OPEN' });
    renderAt('/help/7');

    expect(await screen.findByText('This ticket is resolved. Replying opens it again.')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Reply'), { target: { value: 'It happened again.' } });
    fireEvent.click(screen.getByRole('button', { name: 'Reply and reopen' }));

    expect(await screen.findByText('Open')).toBeTruthy();
    expect(screen.queryByText('This ticket is resolved. Replying opens it again.')).toBeNull();
  });

  it('a student who has not taken the personality test can still open Help', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ number: ME, name: 'Maria Costa',
      institutionalEmail: '50001@students.studymatch.com', role: 'STUDENT', mustChangePassword: false, personalityTestRequired: true }), { status: 200 })));
    render(<StudyMatchProvider><App /></StudyMatchProvider>);

    fireEvent.click(await screen.findByRole('link', { name: 'Need help? Ask the helpdesk' }));
    expect(await screen.findByRole('heading', { name: 'Help' })).toBeTruthy();
    expect(await screen.findByText('My Databases grade is missing')).toBeTruthy();
    fireEvent.click(screen.getByRole('link', { name: 'Back to the personality test' }));
    expect(await screen.findByText('Personality test')).toBeTruthy();
  });
});
