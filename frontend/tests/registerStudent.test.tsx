import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { RegisterStudentPage } from '../src/pages/registration/RegisterStudentPage';
import { StudyMatchProvider } from '../src/studymatch-ui';

const DEGREES = [{ id: 1, name: 'Test Degree', curricularYears: 3 }, { id: 2, name: 'Long Degree', curricularYears: 5 }];
const STUDENT = { name: 'Test Student', personalEmail: 'student@example.com', degreeProgramme: 'Test Degree', curricularYear: 2 };
const REGISTERED = { number: 50001, name: 'Test Student', institutionalEmail: '50001@students.test.com', degreeProgramme: 'Test Degree', curricularYear: 2, emailSent: true };

type Reply = { status: number; body?: unknown };
type Routes = Record<string, Reply | ((init?: RequestInit) => Reply)>;

function mockBackend(routes: Routes) {
  const fetchMock = vi.fn<typeof fetch>(async (url, init) => {
    const key = `${init?.method ?? 'GET'} ${String(url)}`;
    const route = routes[key] ?? { status: 404, body: { code: 'NOT_FOUND' } };
    const reply = typeof route === 'function' ? route(init) : route;
    if (reply.body instanceof Blob) return new Response(reply.body, { status: reply.status });
    return new Response(reply.body === undefined ? null : JSON.stringify(reply.body), { status: reply.status });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderPage() {
  render(<StudyMatchProvider><RegisterStudentPage /></StudyMatchProvider>);
}

async function fillStudent() {
  fireEvent.change(await screen.findByLabelText('Name'), { target: { value: STUDENT.name } });
  fireEvent.change(screen.getByLabelText('Personal email'), { target: { value: STUDENT.personalEmail } });
  fireEvent.change(screen.getByLabelText('Degree programme'), { target: { value: 'Test Degree' } });
  fireEvent.change(screen.getByLabelText('Curricular year'), { target: { value: '2' } });
  fireEvent.click(screen.getByRole('button', { name: 'Register student' }));
}

function bodyOf(fetchMock: ReturnType<typeof mockBackend>, key: string) {
  const call = fetchMock.mock.calls.find(([url, init]) => `${init?.method ?? 'GET'} ${String(url)}` === key);
  return call?.[1]?.body;
}

beforeEach(async () => {
  await i18n.changeLanguage('en');
  document.cookie = 'XSRF-TOKEN=test-csrf-token';
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('register one student', () => {
  it('only offers the years that exist in the chosen degree', async () => {
    mockBackend({ 'GET /api/degrees/': { status: 200, body: DEGREES } });
    renderPage();

    fireEvent.change(await screen.findByLabelText('Degree programme'), { target: { value: 'Test Degree' } });

    const years = within(screen.getByLabelText('Curricular year')).getAllByRole('option').filter(option => (option as HTMLOptionElement).value);
    expect(years.map(option => option.textContent)).toEqual(['Year 1', 'Year 2', 'Year 3']);
  });

  it('registers the student and shows the generated number and email', async () => {
    const fetchMock = mockBackend({
      'GET /api/degrees/': { status: 200, body: DEGREES },
      'POST /api/students/': { status: 201, body: REGISTERED },
    });
    renderPage();

    await fillStudent();

    expect(await screen.findByRole('heading', { name: 'Student registered' })).toBeTruthy();
    expect(screen.getByText('50001@students.test.com')).toBeTruthy();
    expect(bodyOf(fetchMock, 'POST /api/students/')).toBe(JSON.stringify(STUDENT));
  });

  it('shows the degree years when the year does not exist in the degree', async () => {
    mockBackend({
      'GET /api/degrees/': { status: 200, body: DEGREES },
      'POST /api/students/': { status: 400, body: { code: 'CURRICULAR_YEAR_NOT_IN_DEGREE', params: { years: 3 } } },
    });
    renderPage();

    await fillStudent();

    expect(await screen.findByText('This degree programme only has 3 years.')).toBeTruthy();
  });

  it('highlights the missing fields the backend reports', async () => {
    mockBackend({
      'GET /api/degrees/': { status: 200, body: DEGREES },
      'POST /api/students/': { status: 400, body: { code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'name', code: 'NOT_BLANK' }] } },
    });
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: 'Register student' }));

    expect(await screen.findByText('This field is required.')).toBeTruthy();
  });

  it('lets staff send the credentials again, to a corrected email, when the email failed', async () => {
    const fetchMock = mockBackend({
      'GET /api/degrees/': { status: 200, body: DEGREES },
      'POST /api/students/': { status: 201, body: { ...REGISTERED, emailSent: false } },
      'POST /api/students/50001/resend-credentials': { status: 204 },
    });
    renderPage();

    await fillStudent();
    await screen.findByRole('heading', { name: 'Student registered' });
    expect(screen.getByText("The account was created, but the email with the credentials couldn't be delivered. Check the personal email and send them again.")).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Personal email'), { target: { value: 'fixed@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send credentials again' }));

    expect(await screen.findByText('New credentials sent.')).toBeTruthy();
    expect(bodyOf(fetchMock, 'POST /api/students/50001/resend-credentials')).toBe(JSON.stringify({ personalEmail: 'fixed@example.com' }));
  });
});

describe('import students from a CSV file', () => {
  const PREVIEW = [
    { row: 2, student: STUDENT, errors: [] },
    { row: 3, student: { ...STUDENT, name: '', personalEmail: 'other@example.com' }, errors: [{ field: 'name', code: 'NOT_BLANK' }] },
    { row: 4, student: STUDENT, errors: [{ field: 'personalEmail', code: 'DUPLICATE_IN_FILE' }] },
  ];

  async function uploadFile() {
    fireEvent.click(await screen.findByRole('tab', { name: 'Import from CSV' }));
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [new File(['name,personalEmail,degreeProgramme,curricularYear\n'], 'students.csv', { type: 'text/csv' })] } });
  }

  it('shows every row with its errors before anything is created', async () => {
    const fetchMock = mockBackend({
      'GET /api/degrees/': { status: 200, body: DEGREES },
      'POST /api/students/import/preview': { status: 200, body: PREVIEW },
    });
    renderPage();

    await uploadFile();

    expect(await screen.findByText('1 ready to import, 2 with errors. Nothing is created until you confirm.')).toBeTruthy();
    expect(screen.getByText('Name: This field is required.')).toBeTruthy();
    expect(screen.getByText('Personal email: This personal email appears more than once in the file.')).toBeTruthy();
    expect(bodyOf(fetchMock, 'POST /api/students/import/preview')).toBeInstanceOf(FormData);
  });

  it('imports only the valid rows and reports each outcome', async () => {
    const fetchMock = mockBackend({
      'GET /api/degrees/': { status: 200, body: DEGREES },
      'POST /api/students/import/preview': { status: 200, body: PREVIEW },
      'POST /api/students/import': { status: 200, body: [{ row: 2, status: 'EMAIL_NOT_SENT', number: 50001, institutionalEmail: '50001@students.test.com', errors: [] }] },
    });
    renderPage();

    await uploadFile();
    fireEvent.click(await screen.findByRole('button', { name: 'Import 1 students' }));

    expect(await screen.findByText('Email not sent')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Send credentials again' })).toBeTruthy();
    expect(bodyOf(fetchMock, 'POST /api/students/import')).toBe(JSON.stringify({ rows: [{ row: 2, student: STUDENT }] }));
  });

  it('creates nothing when the preview is cancelled', async () => {
    const fetchMock = mockBackend({
      'GET /api/degrees/': { status: 200, body: DEGREES },
      'POST /api/students/import/preview': { status: 200, body: PREVIEW },
    });
    renderPage();

    await uploadFile();
    fireEvent.click(await screen.findByRole('button', { name: 'Cancel' }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Download template' })).toBeTruthy());
    expect(bodyOf(fetchMock, 'POST /api/students/import')).toBeUndefined();
  });

  it('shows a file-level error and creates nothing', async () => {
    mockBackend({
      'GET /api/degrees/': { status: 200, body: DEGREES },
      'POST /api/students/import/preview': { status: 400, body: { code: 'CSV_MISSING_COLUMNS', params: { columns: 'curricularYear' } } },
    });
    renderPage();

    await uploadFile();

    expect(await screen.findByText('The file is missing these columns: curricularYear.')).toBeTruthy();
  });
});
