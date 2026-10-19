import type { StudyRoom, StudyRoomPerson } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';

export const DEMO_SUBJECTS = [
  { id: 'programming', name: 'Programming Lab', color: '#ef535b' },
  { id: 'statistics', name: 'Statistics', color: '#6fba7b' },
  { id: 'quality', name: 'Software Quality', color: '#51bfd7' },
  { id: 'ai', name: 'Artificial Intelligence', color: '#df8fdf' },
  { id: 'management', name: 'Project Management', color: '#9b6bd1' },
] as const;

export type SubjectId = typeof DEMO_SUBJECTS[number]['id'];
export type StudyRoomsView = 'calendar' | 'inscriptions' | 'created';
export type DemoStudent = Pick<CurrentUser, 'number' | 'name'>;

export interface DemoStudyRoom extends StudyRoom {
  subjectId: SubjectId;
  start: Date;
  end: Date;
  rating: number;
  ratingCount: number;
}

export const dateKey = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

export const monthStart = (date: Date) => new Date(date.getFullYear(), date.getMonth(), 1);
export const shiftMonth = (date: Date, offset: number) => new Date(date.getFullYear(), date.getMonth() + offset, 1);

export function monthDays(month: Date) {
  const first = monthStart(month);
  const offset = (first.getDay() + 6) % 7;
  return Array.from({ length: 42 }, (_, index) => new Date(first.getFullYear(), first.getMonth(), 1 - offset + index));
}

export const demoStudent = (user: DemoStudent): StudyRoomPerson => ({ id: `student-${user.number}`, name: user.name });

interface DemoSession {
  id: string;
  subjectId: SubjectId;
  topic: string;
  day: number;
  tutor: 'leonor' | 'tiago' | 'beatriz' | 'ines' | 'me';
  places: number;
  capacity?: number;
  joined?: boolean;
  hour?: number;
}

const SESSIONS: DemoSession[] = [
  { id: 'clean-code', subjectId: 'programming', topic: 'Clean code, clear thinking', day: 5, tutor: 'tiago', places: 5 },
  { id: 'probability', subjectId: 'statistics', topic: 'Making sense of probability', day: 6, tutor: 'leonor', places: 6, joined: true },
  { id: 'quality-basics', subjectId: 'quality', topic: 'Software quality essentials', day: 7, tutor: 'me', places: 4 },
  { id: 'search-algorithms', subjectId: 'ai', topic: 'Finding your way with search algorithms', day: 8, tutor: 'ines', places: 8 },
  { id: 'sprint-planning', subjectId: 'management', topic: 'Plan your first sprint', day: 9, tutor: 'me', places: 4 },
  { id: 'java-collections', subjectId: 'programming', topic: 'Java collections in practice', day: 12, tutor: 'tiago', places: 11 },
  { id: 'regression', subjectId: 'statistics', topic: 'A friendly guide to regression', day: 13, tutor: 'leonor', places: 7 },
  { id: 'hypothesis', subjectId: 'statistics', topic: 'Hypothesis tests without fear', day: 14, tutor: 'leonor', places: 5, joined: true, hour: 18 },
  { id: 'unit-testing', subjectId: 'quality', topic: 'Write your first unit tests', day: 14, tutor: 'beatriz', places: 6, hour: 16 },
  { id: 'neural-networks', subjectId: 'ai', topic: 'Neural networks, one step at a time', day: 15, tutor: 'ines', places: 8, capacity: 8 },
  { id: 'project-kickoff', subjectId: 'management', topic: 'From project idea to action plan', day: 16, tutor: 'me', places: 3 },
  { id: 'java-debugging', subjectId: 'programming', topic: 'Debugging Java together', day: 19, tutor: 'tiago', places: 4, joined: true },
  { id: 'quality-review', subjectId: 'quality', topic: 'A better code review', day: 20, tutor: 'beatriz', places: 6, joined: true },
  { id: 'model-evaluation', subjectId: 'ai', topic: 'How good is your model?', day: 21, tutor: 'ines', places: 4 },
  { id: 'risk-management', subjectId: 'management', topic: 'Spot the risks before they happen', day: 22, tutor: 'beatriz', places: 2 },
  { id: 'stats-revision', subjectId: 'statistics', topic: 'Statistics revision, together', day: 23, tutor: 'leonor', places: 7, joined: true },
  { id: 'exam-practice', subjectId: 'programming', topic: 'Programming exam practice', day: 26, tutor: 'tiago', places: 6 },
  { id: 'quality-checklist', subjectId: 'quality', topic: 'Build your quality checklist', day: 28, tutor: 'beatriz', places: 3, joined: true },
];

export function createDemoStudyRooms(user: DemoStudent, month: Date): DemoStudyRoom[] {
  const student = demoStudent(user);
  const tutors = {
    leonor: { id: 'leonor', name: 'Leonor Faria', rating: 4.7, ratingCount: 9 },
    tiago: { id: 'tiago', name: 'Tiago Costa', rating: 4.9, ratingCount: 18 },
    beatriz: { id: 'beatriz', name: 'Beatriz Santos', rating: 4.8, ratingCount: 12 },
    ines: { id: 'ines', name: 'Inês Martins', rating: 4.6, ratingCount: 7 },
    me: { ...student, rating: 4.9, ratingCount: 6 },
  };

  return SESSIONS.map<DemoStudyRoom>((session) => {
    const subject = DEMO_SUBJECTS.find((item) => item.id === session.subjectId)!;
    const tutor = tutors[session.tutor];
    const start = new Date(month.getFullYear(), month.getMonth(), session.day, session.hour ?? 17, 30);
    const participants: StudyRoomPerson[] = Array.from({ length: session.places - (session.joined ? 1 : 0) }, (_, index) => ({
      id: `${session.id}-participant-${index}`, name: `Demo student ${index + 1}`,
    }));
    if (session.joined) participants.push(student);

    return {
      id: session.id, subjectId: session.subjectId, subject: subject.name, topic: session.topic,
      start, end: new Date(start.getTime() + 60 * 60 * 1000), tutor,
      capacity: session.capacity ?? 12, participants, status: 'SCHEDULED',
      rating: tutor.rating, ratingCount: tutor.ratingCount,
    };
  }).sort((a, b) => a.start.getTime() - b.start.getTime());
}
