import { AnimatePresence, motion } from 'framer-motion';
import { ArrowLeft, ArrowRight, Sparkles } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { AchievementTile } from '../../achievements/Achievement';
import { PROFILE_KEYS, PROFILES, type ProfileKey } from '../../components/badge/Badge';
import { Button } from '../../components/button/Button';
import { Card } from '../../components/card/Card';
import { ProfileBars } from '../../components/charts/Charts';
import { Banner } from '../../components/overlay/Banner';
import { ProgressBar } from '../../components/progress/Progress';
import { cx } from '../../utils/cx';
import { EASE_OUT } from '../../utils/shared';
import './personality.css';

export interface PersonalityQuestion {
  id: string;
  text: string;
  profile: ProfileKey;
  /** Reverse-scored statement. */
  reverse?: boolean;
}

export interface PersonalityResult {
  /** 0–100 per profile. */
  scores: Record<ProfileKey, number>;
  dominant: ProfileKey;
  answers: Record<string, number>;
}

/** 12 statements, 3 per profile, interleaved. Replace with your validated instrument. */
export const DEFAULT_PERSONALITY_QUESTIONS: PersonalityQuestion[] = [
  { id: 'l1', profile: 'leadership', text: 'When a group has no clear direction, I naturally take charge.' },
  { id: 't1', profile: 'technical', text: 'I enjoy breaking a hard problem into smaller pieces I can solve.' },
  { id: 'c1', profile: 'creativity', text: 'I often come up with ideas no one else in the group has thought of.' },
  { id: 'e1', profile: 'execution', text: 'I keep track of deadlines and make sure we hit them.' },
  { id: 'l2', profile: 'leadership', text: 'I like splitting the work and making sure everyone knows their part.' },
  { id: 't2', profile: 'technical', text: 'I’m usually the one who figures out how to make things actually work.' },
  { id: 'c2', profile: 'creativity', text: 'I prefer exploring several options before choosing one.' },
  { id: 'e2', profile: 'execution', text: 'I finish what I start, even the boring parts.' },
  { id: 'l3', profile: 'leadership', text: 'People often look to me to make the final call.' },
  { id: 't3', profile: 'technical', text: 'I like to understand how something works before I use it.' },
  { id: 'c3', profile: 'creativity', text: 'I care about making things look and feel great, not just work.' },
  { id: 'e3', profile: 'execution', text: 'I’d rather deliver something solid than keep planning.' },
];

export const LIKERT = ['Strongly disagree', 'Disagree', 'Neutral', 'Agree', 'Strongly agree'] as const;

/** Average agreement per profile, scaled to 0–100. */
export function scorePersonality(questions: PersonalityQuestion[], answers: Record<string, number>): PersonalityResult {
  const sum = {} as Record<ProfileKey, number>;
  const count = {} as Record<ProfileKey, number>;
  PROFILE_KEYS.forEach((k) => {
    sum[k] = 0;
    count[k] = 0;
  });
  questions.forEach((q) => {
    const a = answers[q.id];
    if (!a) return;
    const v = q.reverse ? 6 - a : a;
    sum[q.profile] += v - 1;
    count[q.profile] += 1;
  });
  const scores = {} as Record<ProfileKey, number>;
  PROFILE_KEYS.forEach((k) => (scores[k] = count[k] ? Math.round((sum[k] / (count[k] * 4)) * 100) : 0));
  const dominant = [...PROFILE_KEYS].sort((a, b) => scores[b] - scores[a])[0];
  return { scores, dominant, answers };
}

export interface PersonalityTestProps {
  questions?: PersonalityQuestion[];
  /** Persist the result; the test shows the result screen after it resolves. */
  onComplete: (result: PersonalityResult) => Promise<void> | void;
  /** Leave the result screen (e.g. navigate to the dashboard). */
  onContinue?: () => void;
  academicYear?: string;
  className?: string;
}

/**
 * Take Personality Test use case. One statement at a time, 1–5 answers
 * (keys 1–5 work), progress, back navigation and an animated result.
 */
export function PersonalityTest({ questions = DEFAULT_PERSONALITY_QUESTIONS, onComplete, onContinue, academicYear, className }: PersonalityTestProps) {
  const [phase, setPhase] = useState<'intro' | 'questions' | 'saving' | 'result'>('intro');
  const [i, setI] = useState(0);
  const [answers, setAnswers] = useState<Record<string, number>>({});
  const [result, setResult] = useState<PersonalityResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const q = questions[Math.min(i, questions.length - 1)];
  const answered = Object.keys(answers).length;
  const index = useRef(i);
  index.current = i;
  const timer = useRef<number | undefined>(undefined);
  useEffect(() => () => window.clearTimeout(timer.current), []);

  const finish = async (all: Record<string, number>) => {
    const r = scorePersonality(questions, all);
    setPhase('saving');
    setError(null);
    try {
      await onComplete(r);
      setResult(r);
      setPhase('result');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'We couldn’t save your result. Your answers are kept; try again.');
      setPhase('questions');
    }
  };

  /** Records the answer for that question, then moves on once (fast double clicks never skip a question). */
  const answer = (questionId: string, v: number) => {
    setAnswers((a) => ({ ...a, [questionId]: v }));
    const at = questions.findIndex((x) => x.id === questionId);
    if (at !== index.current || at >= questions.length - 1) return;
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setI((x) => (x === at ? x + 1 : x)), 220);
  };

  useEffect(() => {
    if (phase !== 'questions') return;
    const onKey = (e: KeyboardEvent) => {
      const n = Number(e.key);
      if (n >= 1 && n <= 5 && !(e.target instanceof HTMLInputElement)) answer(q.id, n);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  if (phase === 'intro') {
    return (
      <Card padding="lg" className={cx('sm-pt', className)}>
        <div className="sm-pt__intro">
          <AchievementTile id="know-yourself" artSize={150} showRarity={false} style={{ width: 200 }} />
          <div>
            <span className="sm-eyebrow">Personality test{academicYear ? ` · ${academicYear}` : ''}</span>
            <h2 className="sm-h2" style={{ marginTop: 10 }}>
              How do you work in a team?
            </h2>
            <p className="sm-pt__lead">
              {questions.length} quick statements, about 3 minutes. There are no right answers: be honest, it helps us build groups where everyone has a role.
            </p>
            <Banner tone="info">You need to complete this test once per academic year to use StudyMatch.</Banner>
            <div style={{ marginTop: 20 }}>
              <Button size="lg" iconRight={ArrowRight} onClick={() => setPhase('questions')}>
                Start the test
              </Button>
            </div>
          </div>
        </div>
      </Card>
    );
  }

  if (phase === 'result' && result) {
    const P = PROFILES[result.dominant];
    const Icon = P.icon;
    return (
      <Card padding="lg" className={cx('sm-pt', className)}>
        <div className="sm-pt__result">
          <motion.div className="sm-pt__hero" style={{ background: `color-mix(in srgb, ${P.color} 14%, white)` }} initial={{ scale: 0.9, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={{ duration: 0.6, ease: EASE_OUT }}>
            <span className="sm-pt__hero-icon" style={{ background: P.color }}>
              <Icon size={30} strokeWidth={2.25} aria-hidden />
            </span>
            <span className="sm-eyebrow">Your strongest profile</span>
            <h2 className="sm-h2">{P.label}</h2>
            <p>{P.description}</p>
          </motion.div>
          <div>
            <h3 className="sm-title" style={{ marginBottom: 16 }}>
              Your profile
            </h3>
            <ProfileBars values={result.scores} />
            <p className="sm-caption" style={{ marginTop: 16 }}>
              Your profile is used to balance groups this academic year. Only you, your teachers and staff can see it.
            </p>
            {onContinue && (
              <div style={{ marginTop: 20 }}>
                <Button size="lg" icon={Sparkles} onClick={onContinue}>
                  Continue to StudyMatch
                </Button>
              </div>
            )}
          </div>
        </div>
      </Card>
    );
  }

  const current = answers[q.id];
  const isLast = i === questions.length - 1;
  return (
    <Card padding="lg" className={cx('sm-pt', className)}>
      <div className="sm-pt__top">
        <Button variant="ghost" size="sm" icon={ArrowLeft} onClick={() => (i === 0 ? setPhase('intro') : setI(i - 1))}>
          Back
        </Button>
        <span className="sm-num sm-caption">
          {i + 1} of {questions.length}
        </span>
      </div>
      <ProgressBar value={answered} max={questions.length} label="Test progress" />
      {error && (
        <div style={{ marginTop: 16 }}>
          <Banner tone="danger">{error}</Banner>
        </div>
      )}
      <AnimatePresence mode="wait">
        <motion.div key={q.id} initial={{ opacity: 0, x: 24 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -24 }} transition={{ duration: 0.25, ease: EASE_OUT }}>
          <p className="sm-pt__q" id={`q-${q.id}`}>
            {q.text}
          </p>
          <div className="sm-pt__scale" role="radiogroup" aria-labelledby={`q-${q.id}`}>
            {LIKERT.map((label, k) => {
              const v = k + 1;
              return (
                <button key={v} type="button" role="radio" aria-checked={current === v} className="sm-pt__opt" data-v={v} onClick={() => answer(q.id, v)}>
                  <span className="sm-pt__dot" aria-hidden />
                  <span>{label}</span>
                  <kbd aria-hidden>{v}</kbd>
                </button>
              );
            })}
          </div>
        </motion.div>
      </AnimatePresence>
      <div className="sm-pt__bottom">
        <span className="sm-caption">Tip: use keys 1–5</span>
        {isLast && (
          <Button size="lg" disabled={answered < questions.length} loading={phase === 'saving'} onClick={() => finish(answers)}>
            See my profile
          </Button>
        )}
      </div>
    </Card>
  );
}
