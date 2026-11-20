import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { StaffArea } from '../src/pages/staff/StaffArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { StudentRecordData } from '../src/types/studentDirectory';

const withoutStatute: StudentRecordData = {
  profile: { number: 50001, name: 'Ana Silva', banner: null, pfp: null, aboutMe: null, skills: [], interests: [], languages: [], tutorBadges: [], achievements: [], privateProfile: null },
  personalEmail: 'ana.personal@example.com', accountStatus: 'ACTIVE', statuteType: null, statuteEndDate: null,
  averageGrade: null, categoryLevels: [], enrolments: [],
};
const working: StudentRecordData = { ...withoutStatute, statuteType: 'WORKING_STUDENT', statuteEndDate: '2030-09-01' };
const reply = (body: unknown, status = 200) => new Response(body === null ? null : JSON.stringify(body), { status });

type Answer = (method: string, body: unknown) => Response;

// GET gives the record, PUT and DELETE on the statute are answered by the test
function backend(record: StudentRecordData, statute: Answer = () => reply(null, 204)) {
  const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    if (url.pathname === '/api/students/50001') return reply(record);
    if (url.pathname === '/api/students/50001/statute') return statute(init?.method ?? 'GET', init?.body ? JSON.parse(String(init.body)) : null);
    throw new Error(`Unexpected request: ${url.pathname}`);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function open() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={['/students/50001']}><StaffArea currentUser={{ number: 9, name: 'Test Staff', institutionalEmail: 'staff@school.pt', role: 'STAFF', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

const statuteCard = async () => within((await screen.findByText(i18n.t('directory.specialStatute'))).closest('section, article, div.sm-card') as HTMLElement);

async function fillAndSave(type: string, endDate: string) {
  const dialog = within(await screen.findByRole('dialog'));
  fireEvent.change(dialog.getByLabelText(i18n.t('directory.statuteType')), { target: { value: type } });
  fireEvent.change(dialog.getByLabelText(i18n.t('directory.statuteEnd')), { target: { value: endDate } });
  fireEvent.click(dialog.getByRole('button', { name: i18n.t('common.save') }));
}

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('special statute (staff)', () => {
  it('adds a statute to a student without one and the card shows it', async () => {
    const sent: unknown[] = [];
    backend(withoutStatute, (method, body) => { sent.push({ method, body }); return reply({ ...working, statuteType: 'STUDENT_PARENT' }); });
    open();
    expect((await statuteCard()).getByText(i18n.t('directory.noStatute'))).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: i18n.t('directory.addStatute') }));
    await fillAndSave('STUDENT_PARENT', '2030-09-01');

    expect(await screen.findByText(i18n.t('directory.statuteSaved'))).toBeTruthy();
    expect(sent).toEqual([{ method: 'PUT', body: { type: 'STUDENT_PARENT', endDate: '2030-09-01' } }]);
    expect((await statuteCard()).getByText(i18n.t('statutes.STUDENT_PARENT'))).toBeTruthy();
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  });

  it('replaces the current statute, even with the same type', async () => {
    const sent: unknown[] = [];
    backend(working, (method, body) => { sent.push({ method, body }); return reply({ ...working, statuteEndDate: '2031-01-31' }); });
    open();
    fireEvent.click(await screen.findByRole('button', { name: i18n.t('directory.replaceStatute') }));
    await fillAndSave('WORKING_STUDENT', '2031-01-31');

    expect(await screen.findByText(i18n.t('directory.statuteSaved'))).toBeTruthy();
    expect(sent).toEqual([{ method: 'PUT', body: { type: 'WORKING_STUDENT', endDate: '2031-01-31' } }]);
    expect((await statuteCard()).getByText('Jan 31, 2031')).toBeTruthy();
  });

  it('shows a past end date under the end date and keeps the form open', async () => {
    backend(withoutStatute, () => reply({ code: 'STATUTE_END_DATE_IN_PAST' }, 400));
    open();
    fireEvent.click(await screen.findByRole('button', { name: i18n.t('directory.addStatute') }));
    await fillAndSave('SPECIAL_NEEDS', '2020-01-01');

    const endDate = await screen.findByLabelText(i18n.t('directory.statuteEnd'));
    await waitFor(() => expect(endDate.getAttribute('aria-invalid')).toBe('true'));
    expect(screen.getByText(i18n.t('errors.STATUTE_END_DATE_IN_PAST'))).toBeTruthy();
    expect(screen.getByRole('dialog')).toBeTruthy();
    expect((await statuteCard()).getByText(i18n.t('directory.noStatute'))).toBeTruthy();
  });

  it('ends the statute only after confirming, and the card shows none', async () => {
    const fetchMock = backend(working);
    open();
    fireEvent.click(await screen.findByRole('button', { name: i18n.t('directory.endStatute') }));
    const confirm = within(await screen.findByRole('dialog'));
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false);

    fireEvent.click(confirm.getByRole('button', { name: i18n.t('directory.endStatute') }));

    expect(await screen.findByText(i18n.t('directory.statuteEnded'))).toBeTruthy();
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(true);
    expect((await statuteCard()).getByText(i18n.t('directory.noStatute'))).toBeTruthy();
  });

  it('a statute someone already ended gives a toast and the card catches up', async () => {
    backend(working, () => reply({ code: 'STUDENT_HAS_NO_STATUTE' }, 409));
    open();
    fireEvent.click(await screen.findByRole('button', { name: i18n.t('directory.endStatute') }));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: i18n.t('directory.endStatute') }));

    expect(await screen.findByText(i18n.t('errors.STUDENT_HAS_NO_STATUTE'))).toBeTruthy();
    expect((await statuteCard()).getByText(i18n.t('directory.noStatute'))).toBeTruthy();
  });
});
