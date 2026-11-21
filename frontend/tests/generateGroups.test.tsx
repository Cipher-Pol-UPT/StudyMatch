import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FINISH_MS, GeneratingGroups } from '../src/components/groups/GeneratingGroups';
import i18n from '../src/i18n';
import { TeacherArea } from '../src/pages/teacher/TeacherArea';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { GradeColumn } from '../src/types/grades';
import type { GroupGenerationData, GroupPreviewData } from '../src/types/groups';
import type { SubjectEditionData } from '../src/types/studentDirectory';

const edition: SubjectEditionData = { id: 7, academicYear: 2026, subject: { id: 3, code: 'TST', name: 'Test Subject', ects: 6, category: { id: 1, name: 'Test Category' }, degrees: [] }, teachers: [{ number: 9, name: 'Test Teacher' }], classSections: ['T1'] };
const project: GradeColumn = { id: 1, name: 'Test Project', type: 'PROJECT', weight: 60, groupWork: true };
const exam: GradeColumn = { id: 2, name: 'Test Exam', type: 'EXAM', weight: 40, groupWork: false };
const draft: GroupPreviewData[] = [
  { number: 1, size: 2, sharedLanguages: ['PORTUGUESE', 'ENGLISH'], members: [{ number: 50001, name: 'Test Ana', classSection: 'T1' }, { number: 50002, name: 'Test Rui', classSection: 'T1' }] },
  { number: 2, size: 2, sharedLanguages: [], members: [{ number: 50003, name: 'Test Eva', classSection: 'T1' }, { number: 50004, name: 'Test Leo', classSection: 'T1' }] },
];
// a small class: two groups of two, nothing to search
const smallClass: GroupGenerationData = { students: 4, groups: 2, optimalSize: 2, searchMillis: 0 };
const reply = (body: unknown, status = 200) => new Response(status === 204 ? null : JSON.stringify(body), { status });

// each groups call is answered by the test, in the order they come
function backend(answers: Record<string, (url: URL) => Response> = {}) {
  const calls: string[] = [];
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input), 'https://test.invalid');
    const method = init?.method ?? 'GET';
    if (url.pathname === '/api/subject-editions/') return reply([edition]);
    if (url.pathname === '/api/subject-editions/7/grades') return reply({ assignments: [project, exam], students: [], weightsComplete: true });
    // what generating will do comes with every generate, answered unless the test says otherwise
    if (url.pathname.endsWith('/groups/generation') && !answers[`GET ${url.pathname}`]) return reply(smallClass);
    const key = `${method} ${url.pathname}`;
    calls.push(`${key}${url.search}`);
    const answer = answers[key];
    if (answer) return answer(url);
    throw new Error(`Unexpected request: ${key}`);
  }));
  return calls;
}

function open(path = '/subject-editions/7/assignments/1/groups') {
  render(<StudyMatchProvider><MemoryRouter initialEntries={[path]}><TeacherArea currentUser={{ number: 9, name: 'Test Teacher', institutionalEmail: 'teacher@school.pt', role: 'TEACHER', mustChangePassword: false, personalityTestRequired: false }} onLoggedOut={vi.fn()} /></MemoryRouter></StudyMatchProvider>);
}

const GENERATE = 'POST /api/subject-editions/7/assignments/1/groups';
const button = (name: string) => screen.findByRole('button', { name });

beforeEach(async () => { await i18n.changeLanguage('en'); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('generate groups (teacher)', () => {
  it('only offers groups on group work assignments', async () => {
    backend();
    open('/subject-editions/7/assignments');

    // the table waits on two calls (the edition, then the grade sheet), slow when the whole suite runs
    expect(await screen.findByRole('link', { name: 'Groups of Test Project' }, { timeout: 3000 })).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'Groups of Test Exam' })).toBeNull();
  });

  it('generates a draft and shows each group with its members and shared languages', async () => {
    backend({ [GENERATE]: () => reply(draft, 201) });
    open();

    fireEvent.click(await button('Generate groups'));

    expect(await screen.findByText('Group 1')).toBeTruthy();
    expect(screen.getByText('Test Ana')).toBeTruthy();
    expect(screen.getByText('Shares Portuguese, English.')).toBeTruthy();
    // no language in common is pointed out
    expect(screen.getByText('These students have no language in common.')).toBeTruthy();
    expect(screen.getAllByText('Draft')).toHaveLength(2);
  });

  it('shows the loading bar on the first generate too, until the draft is there', async () => {
    backend({ [GENERATE]: () => reply(draft, 201) });
    open();

    fireEvent.click(await button('Generate groups'));

    expect(await screen.findByRole('progressbar', { name: 'Generating groups…' })).toBeTruthy();
    expect(await screen.findByText('Group 1')).toBeTruthy();
    expect(screen.queryByRole('progressbar')).toBeNull();
  });

  it('shows a loading bar while generating, with the actions disabled', async () => {
    let finish: (response: Response) => void = () => undefined;
    backend({ [GENERATE]: () => reply(draft, 201) });
    open();
    fireEvent.click(await button('Generate groups'));
    await screen.findByText('Group 1');
    // the regenerate call waits until the test lets it go
    vi.mocked(fetch).mockImplementationOnce(() => new Promise<Response>(resolve => { finish = resolve; }));

    fireEvent.click(screen.getByRole('button', { name: 'Regenerate' }));

    expect(await screen.findByRole('progressbar', { name: 'Generating groups…' })).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Confirm and publish' }) as HTMLButtonElement).disabled).toBe(true);
    finish(reply(draft, 201));
    await waitFor(() => expect(screen.queryByRole('progressbar')).toBeNull());
  });

  it('regenerating replaces the draft with the new one', async () => {
    let round = 0;
    const calls = backend({ [GENERATE]: () => reply(round++ === 0 ? draft : [draft[0]], 201) });
    open();
    fireEvent.click(await button('Generate groups'));
    await screen.findByText('Group 2');

    fireEvent.click(screen.getByRole('button', { name: 'Regenerate' }));

    await waitFor(() => expect(screen.queryByText('Group 2')).toBeNull());
    expect(calls.filter(call => call.startsWith(GENERATE))).toHaveLength(2);
  });

  it('confirming publishes the groups and tells the teacher', async () => {
    const calls = backend({ [GENERATE]: () => reply(draft, 201), 'POST /api/subject-editions/7/assignments/1/groups/publish': () => reply(null, 204) });
    open();
    fireEvent.click(await button('Generate groups'));

    fireEvent.click(await button('Confirm and publish'));

    expect(await screen.findByText('Groups published. Students can see them now.')).toBeTruthy();
    expect(screen.getAllByText('Published')).toHaveLength(2);
    expect(screen.queryByRole('button', { name: 'Confirm and publish' })).toBeNull();
    expect(calls).toContain('POST /api/subject-editions/7/assignments/1/groups/publish');
  });

  it('discarding the draft goes back to generating', async () => {
    backend({ [GENERATE]: () => reply(draft, 201), 'DELETE /api/subject-editions/7/assignments/1/groups/draft': () => reply(null, 204) });
    open();
    fireEvent.click(await button('Generate groups'));

    fireEvent.click(await button('Discard draft'));

    expect(await screen.findByText('Draft discarded.')).toBeTruthy();
    expect(screen.queryByText('Group 1')).toBeNull();
    expect(screen.getByRole('button', { name: 'Generate groups' })).toBeTruthy();
  });

  it('with published groups it warns first, and only replaces them when the teacher agrees', async () => {
    const calls = backend({
      [GENERATE]: url => url.searchParams.get('replacePublished') === 'true' ? reply(draft, 201) : reply({ code: 'GROUPS_ALREADY_PUBLISHED' }, 409),
    });
    open();
    fireEvent.click(await button('Generate groups'));

    const dialog = within(await screen.findByRole('dialog'));
    expect(dialog.getByText('Generate new groups?')).toBeTruthy();
    fireEvent.click(dialog.getByRole('button', { name: 'Generate new groups' }));

    expect(await screen.findByText('Group 1')).toBeTruthy();
    expect(calls).toEqual([GENERATE, `${GENERATE}?replacePublished=true`]);
  });

  it('keeping the published groups sends nothing else', async () => {
    const calls = backend({ [GENERATE]: () => reply({ code: 'GROUPS_ALREADY_PUBLISHED' }, 409) });
    open();
    fireEvent.click(await button('Generate groups'));

    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Keep them' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(calls).toEqual([GENERATE]);
  });

  it('shows the other errors in a banner', async () => {
    backend({ [GENERATE]: () => reply({ code: 'NO_VALID_GROUP_SPLIT' }, 400) });
    open();

    fireEvent.click(await button('Generate groups'));

    expect(await screen.findByText(i18n.t('errors.NO_VALID_GROUP_SPLIT'))).toBeTruthy();
    expect(screen.queryByText('Group 1')).toBeNull();
  });
});

describe('the loader while groups are generated', () => {
  const current = () => screen.getByRole('listitem', { current: 'step' }).textContent;
  const progress = () => Number(screen.getByRole('progressbar').getAttribute('aria-valuenow'));
  const students = (container: HTMLElement) => container.querySelectorAll('.generating-groups__student');
  const bigClass: GroupGenerationData = { students: 40, groups: 10, optimalSize: 4, searchMillis: 8000 };

  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  it("follows the server's search time, with its groups and their size", () => {
    render(<GeneratingGroups plan={bigClass} expectedMs={8000} done={false} onFinished={vi.fn()} />);
    expect(screen.getByText('Generating 10 groups of 4…')).toBeTruthy();
    expect(screen.getByText('About 8 seconds')).toBeTruthy();

    act(() => { vi.advanceTimersByTime(4000); });

    // half the search time is half the bar
    expect(progress()).toBeGreaterThan(45);
    expect(progress()).toBeLessThan(50);
    act(() => { vi.advanceTimersByTime(1000); });
    expect(current()).toContain('Finding a shared language');
  });

  it('waits short of the end while the server is still working', () => {
    render(<GeneratingGroups plan={bigClass} expectedMs={8000} done={false} onFinished={vi.fn()} />);

    act(() => { vi.advanceTimersByTime(30_000); });

    expect(progress()).toBeLessThan(100);
    expect(progress()).toBeGreaterThan(90);
  });

  it('fills fast and hands over as soon as the groups are back, even halfway', () => {
    const onFinished = vi.fn();
    const { rerender } = render(<GeneratingGroups plan={bigClass} expectedMs={8000} done={false} onFinished={onFinished} />);
    act(() => { vi.advanceTimersByTime(2000); });

    rerender(<GeneratingGroups plan={bigClass} expectedMs={8000} done onFinished={onFinished} />);

    expect(progress()).toBe(100);
    expect(screen.queryByRole('listitem', { current: 'step' })).toBeNull();
    act(() => { vi.advanceTimersByTime(FINISH_MS - 1); });
    expect(onFinished).not.toHaveBeenCalled();
    act(() => { vi.advanceTimersByTime(1); });
    expect(onFinished).toHaveBeenCalledOnce();
  });

  it('draws three of the groups, one circle per student at the optimal size, all seated at the end', () => {
    const { container, rerender } = render(<GeneratingGroups plan={bigClass} expectedMs={8000} done={false} onFinished={vi.fn()} />);
    expect(students(container)).toHaveLength(3 * 4);
    expect(container.querySelectorAll('.generating-groups__student.is-placed')).toHaveLength(0);

    rerender(<GeneratingGroups plan={bigClass} expectedMs={8000} done onFinished={vi.fn()} />);

    expect(container.querySelectorAll('.generating-groups__student.is-placed')).toHaveLength(12);
  });

  it('a class of two groups of five draws just those', () => {
    const { container } = render(<GeneratingGroups plan={{ students: 10, groups: 2, optimalSize: 5, searchMillis: 3000 }}
      expectedMs={3000} done={false} onFinished={vi.fn()} />);

    expect(students(container)).toHaveLength(2 * 5);
    expect(container.querySelectorAll('.generating-groups__slot')).toHaveLength(2);
  });
});
