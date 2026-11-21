import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { PersonalityTestPage } from '../src/pages/personality/PersonalityTestPage';
import { StudyMatchProvider } from '../src/studymatch-ui';

const PROFILE = { openness: 1, conscientiousness: 0.3333, extraversion: 0.75, agreeableness: 0.8333, neuroticism: 0.6667 };

interface BackendReply {
  status: number;
  body: unknown;
}

function mockBackend(reply: BackendReply = { status: 200, body: PROFILE }) {
  const fetchMock = vi.fn<typeof fetch>(async () => new Response(JSON.stringify(reply.body), { status: reply.status }));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderPage() {
  const onCompleted = vi.fn();
  render(<StudyMatchProvider><PersonalityTestPage onCompleted={onCompleted} /></StudyMatchProvider>);
  return onCompleted;
}

function questionGroups() {
  return screen.getAllByRole('group');
}

function answer(questionIndex: number, option: string) {
  fireEvent.click(within(questionGroups()[questionIndex]).getByRole('radio', { name: option }));
}

function answerAll(option: string) {
  questionGroups().forEach((_, index) => answer(index, option));
}

function submit() {
  fireEvent.click(screen.getByRole('button', { name: 'Submit answers' }));
}

function sentAnswers(fetchMock: ReturnType<typeof mockBackend>) {
  return JSON.parse(String(fetchMock.mock.calls[0][1]?.body)).answers;
}

beforeEach(async () => {
  await i18n.changeLanguage('en');
  document.cookie = 'XSRF-TOKEN=test-csrf-token';
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('personality test', () => {
  it('shows the 20 Mini-IPIP statements in the order the backend scores them', () => {
    mockBackend();
    renderPage();

    const groups = questionGroups();

    expect(groups).toHaveLength(20);
    expect(within(groups[0]).getByText('I am the life of the party.')).toBeTruthy();
    expect(within(groups[14]).getByText('I have difficulty understanding abstract ideas.')).toBeTruthy();
    expect(within(groups[19]).getByText('I do not have a good imagination.')).toBeTruthy();
  });

  it('sends the answers in question order and shows the profile the backend calculated', async () => {
    const fetchMock = mockBackend();
    renderPage();

    answerAll('Moderately accurate');
    answer(0, 'Very accurate');
    answer(19, 'Very inaccurate');
    submit();

    expect(await screen.findByRole('heading', { name: 'Your profile' })).toBeTruthy();
    expect(screen.queryByText('Your strongest profile')).toBeNull();
    expect(screen.getByText('100%')).toBeTruthy();
    expect(sentAnswers(fetchMock)).toEqual([5, ...Array(18).fill(4), 1]);
    expect(fetchMock).toHaveBeenCalledWith('/api/students/personality-test', expect.objectContaining({
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'test-csrf-token' },
    }));
  });

  it('highlights the questions the backend says are unanswered', async () => {
    const fetchMock = mockBackend({
      status: 400,
      body: { code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'answers[3]', code: 'NOT_NULL' }] },
    });
    renderPage();

    answerAll('Neither accurate nor inaccurate');
    submit();

    expect(await screen.findByText('Check the highlighted fields.')).toBeTruthy();
    expect(within(questionGroups()[3]).getByText('This field is required.')).toBeTruthy();
    expect(within(questionGroups()[2]).queryByText('This field is required.')).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('sends unanswered questions as empty so the backend can report them', async () => {
    const fetchMock = mockBackend({ status: 400, body: { code: 'VALIDATION_FAILED', fieldErrors: [] } });
    renderPage();

    answer(0, 'Moderately accurate');
    submit();

    await screen.findByText('Check the highlighted fields.');
    expect(sentAnswers(fetchMock)).toEqual([4, ...Array(19).fill(null)]);
  });

  it('shows the backend message when the test was already taken this year', async () => {
    mockBackend({ status: 409, body: { code: 'PERSONALITY_TEST_ALREADY_TAKEN' } });
    renderPage();

    answerAll('Moderately accurate');
    submit();

    expect(await screen.findByText('You have already taken the personality test this academic year.')).toBeTruthy();
  });

  it('counts the answered questions', () => {
    mockBackend();
    renderPage();

    answer(0, 'Moderately accurate');
    answer(1, 'Moderately inaccurate');

    expect(screen.getByText('2 of 20')).toBeTruthy();
  });

  it('unlocks the platform after seeing the profile', async () => {
    mockBackend();
    const onCompleted = renderPage();

    answerAll('Moderately accurate');
    submit();
    fireEvent.click(await screen.findByRole('button', { name: 'Continue to StudyMatch' }));

    expect(onCompleted).toHaveBeenCalled();
  });
});
