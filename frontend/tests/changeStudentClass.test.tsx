import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { StaffArea } from '../src/pages/staff/StaffArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { EnrolmentRecordData, StudentRecordData, SubjectEditionData } from '../src/types/studentDirectory';

const programming: EnrolmentRecordData = {
  subjectEditionId: 11, subjectCode: 'TST1', subjectName: 'Test Programming', ects: 6, academicYear: 2026,
  classSection: 'T1', status: 'IN_PROGRESS', finalGrade: null,
};
const record: StudentRecordData = {
  profile: { number: 50001, name: 'Test Student', banner: null, pfp: null, aboutMe: null, skills: [], interests: [], languages: [], tutorBadges: [], achievements: [], privateProfile: null },
  personalEmail: 'test.student@example.com', accountStatus: 'ACTIVE', statuteType: null, statuteEndDate: null,
  averageGrade: null, categoryLevels: [], enrolments: [
    programming,
    // a past year's enrolment: its edition isn't current, so no Change class
    { ...programming, subjectEditionId: 5, academicYear: 2025, classSection: 'T4', status: 'FAILED', finalGrade: 7 },
  ],
};
const edition = (id: number, classSections: string[]): SubjectEditionData => ({
  id, academicYear: 2026, teachers: [], classSections,
  subject: { id: 1, code: 'TST1', name: 'Test Programming', ects: 6, category: { id: 1, name: 'Test Category' }, degrees: [] },
});
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

// GET gives the record and this year's editions, the PUT is answered by the test
function backend(editions: SubjectEditionData[], move: (body: unknown) => Response = () => reply(null, 500)) {
  const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    if (url.pathname === '/api/students/50001') return reply(record);
    if (url.pathname === '/api/subject-editions/current') return reply(editions);
    if (url.pathname === '/api/subject-editions/11/enrolments/50001/class-section' && init?.method === 'PUT') {
      return move(JSON.parse(String(init.body)));
    }
    throw new Error(`Unexpected request: ${init?.method ?? 'GET'} ${url.pathname}`);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function open() {
  render(<StudyMatchProvider><MemoryRouter initialEntries={['/students/50001']}><StaffArea currentUser={{ number: 9, name: 'Test Staff', institutionalEmail: 'staff@school.pt', role: 'STAFF', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

const changeButton = () => screen.findByRole('button', { name: 'Change class in Test Programming' });

async function moveTo(classSection: string) {
  fireEvent.click(await changeButton());
  const dialog = within(await screen.findByRole('dialog'));
  fireEvent.change(dialog.getByLabelText('New class'), { target: { value: classSection } });
  fireEvent.click(dialog.getByRole('button', { name: 'Change class' }));
}

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('change a student class (staff)', () => {
  it("moves the student to another class of the edition and the row shows it", async () => {
    const sent: unknown[] = [];
    backend([edition(11, ['T1', 'T2', 'T3'])], body => { sent.push(body); return reply({ ...programming, classSection: 'T2' }); });
    open();

    await moveTo('T2');

    expect(await screen.findByText('Class changed to T2.')).toBeTruthy();
    expect(sent).toEqual([{ classSection: 'T2' }]);
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(screen.getByRole('cell', { name: 'T2' })).toBeTruthy();
  });

  it("only offers the edition's other classes, and only for this year's enrolments", async () => {
    backend([edition(11, ['T1', 'T2'])]);
    open();

    fireEvent.click(await changeButton());

    const options = within(await screen.findByRole('dialog')).getAllByRole('option').map(option => option.textContent);
    expect(options).toEqual(['Choose a class', 'T2']);
    expect(screen.getAllByRole('button', { name: /^Change class in/ })).toHaveLength(1);
  });

  it('closes without changing anything when cancelled', async () => {
    const fetchMock = backend([edition(11, ['T1', 'T2'])]);
    open();

    fireEvent.click(await changeButton());
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);
    expect(screen.getByRole('cell', { name: 'T1' })).toBeTruthy();
  });

  it('shows a class that is not in the edition in a banner and keeps the dialog open', async () => {
    backend([edition(11, ['T1', 'T2'])], () => reply({ code: 'CLASS_SECTION_NOT_IN_SUBJECT_EDITION' }, 400));
    open();

    await moveTo('T2');

    const dialog = within(await screen.findByRole('dialog'));
    expect(await dialog.findByText(i18n.t('errors.CLASS_SECTION_NOT_IN_SUBJECT_EDITION'))).toBeTruthy();
    expect(screen.getByRole('cell', { name: 'T1' })).toBeTruthy();
  });

  it('has no Change class when the edition has only one class', async () => {
    const fetchMock = backend([edition(11, ['T1'])]);
    open();

    expect(await screen.findByRole('cell', { name: 'T4' })).toBeTruthy();
    await waitFor(() => expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith('/subject-editions/current'))).toBe(true));
    expect(screen.queryByRole('button', { name: /^Change class in/ })).toBeNull();
  });
});
