import { useEffect, useState } from 'react';

/**
 * StudyRooms are online only: every room has a Google Meet link.
 * Lifecycle: Scheduled → Full → Completed, or Cancelled from Scheduled / Full.
 */
export type StudyRoomStatus = 'scheduled' | 'full' | 'completed' | 'cancelled';

export interface StudyRoomPerson {
  id: string;
  name: string;
  avatarUrl?: string;
}

export interface StudyRoom {
  id: string;
  subject: string;
  /** Main topic, shown as the title. */
  topic: string;
  topics?: string[];
  description?: string;
  start: Date | string;
  end: Date | string;
  tutor: StudyRoomPerson & { rating?: number; ratings?: number };
  /** 2–20 participants. */
  capacity: number;
  participants: StudyRoomPerson[];
  /** https://meet.google.com/abc-defg-hij */
  meetUrl: string;
  status: StudyRoomStatus;
  /** Tutor confirmed attendance (room is then Completed). */
  attendanceConfirmed?: boolean;
}

/** Where a room is in time. Participants can enter from `ENTER_EARLY_MIN` minutes before the start. */
export type StudyRoomPhase = 'upcoming' | 'soon' | 'live' | 'ended';
export const ENTER_EARLY_MIN = 10;
export const CAPACITY_RANGE = { min: 2, max: 20 } as const;
export const MEET_URL_PATTERN = /^https:\/\/meet\.google\.com\/[a-z]{3}-[a-z]{4}-[a-z]{3}$/;

export const toDate = (d: Date | string) => (typeof d === 'string' ? new Date(d) : d);

export function studyRoomPhase(room: Pick<StudyRoom, 'start' | 'end'>, now = new Date()): StudyRoomPhase {
  const s = toDate(room.start).getTime();
  const e = toDate(room.end).getTime();
  const t = now.getTime();
  if (t >= e) return 'ended';
  if (t >= s) return 'live';
  if (t >= s - ENTER_EARLY_MIN * 60_000) return 'soon';
  return 'upcoming';
}

export interface StudyRoomAccess {
  phase: StudyRoomPhase;
  isHost: boolean;
  joined: boolean;
  seatsLeft: number;
  /** Show the Join button (it may still be disabled with `joinBlocked`). */
  canJoin: boolean;
  /** Why joining is not possible right now. */
  joinBlocked?: string;
  canLeave: boolean;
  /** Can open the Google Meet now. */
  canEnter: boolean;
  /** When the Meet opens for this user. */
  opensAt: Date;
}

/** All the Join / Leave / Enter rules of the use cases in one place. */
export function studyRoomAccess(room: StudyRoom, meId: string | undefined, now = new Date(), joinedRooms: StudyRoom[] = []): StudyRoomAccess {
  const phase = studyRoomPhase(room, now);
  const isHost = !!meId && room.tutor.id === meId;
  const joined = !!meId && room.participants.some((p) => p.id === meId);
  const seatsLeft = Math.max(0, room.capacity - room.participants.length);
  const open = room.status === 'scheduled' || room.status === 'full';
  const started = phase === 'live' || phase === 'ended';
  const opensAt = new Date(toDate(room.start).getTime() - ENTER_EARLY_MIN * 60_000);

  let joinBlocked: string | undefined;
  if (started) joinBlocked = 'Joining closes when the StudyRoom starts.';
  else if (seatsLeft === 0) joinBlocked = 'This StudyRoom is full.';
  else {
    const clash = joinedRooms.find((r) => r.id !== room.id && r.status !== 'cancelled' && toDate(r.start) < toDate(room.end) && toDate(room.start) < toDate(r.end));
    if (clash) joinBlocked = `It overlaps “${clash.topic}”, which you already joined.`;
  }

  return {
    phase,
    isHost,
    joined,
    seatsLeft,
    canJoin: open && !isHost && !joined && phase !== 'ended',
    joinBlocked,
    canLeave: joined && open && !started,
    canEnter: open && (joined || isHost) && (phase === 'soon' || phase === 'live'),
    opensAt,
  };
}

/** Re-render every `ms` so countdowns and the Enter button stay current. */
export function useNow(ms = 30_000, fixed?: Date) {
  const [now, setNow] = useState(() => fixed ?? new Date());
  useEffect(() => {
    if (fixed) return;
    const t = window.setInterval(() => setNow(new Date()), ms);
    return () => window.clearInterval(t);
  }, [ms, fixed]);
  return fixed ?? now;
}

/** "in 8 min", "in 2 h 15 min", "in 3 days". */
export function formatCountdown(target: Date, now = new Date()) {
  const m = Math.max(0, Math.round((target.getTime() - now.getTime()) / 60_000));
  if (m < 1) return 'now';
  if (m < 60) return `in ${m} min`;
  if (m < 60 * 24) return `in ${Math.floor(m / 60)} h${m % 60 ? ` ${m % 60} min` : ''}`;
  const d = Math.round(m / 60 / 24);
  return `in ${d} day${d > 1 ? 's' : ''}`;
}

/** Placeholder for a link your backend creates with the Google Calendar API (conferenceData). */
export function fakeMeetUrl(seed = Math.random().toString(36)) {
  const letters = 'abcdefghijkmnopqrstuvwxyz';
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  const pick = (n: number) =>
    Array.from({ length: n }, () => {
      h = (h * 1103515245 + 12345) >>> 0;
      return letters[h % letters.length];
    }).join('');
  return `https://meet.google.com/${pick(3)}-${pick(4)}-${pick(3)}`;
}
