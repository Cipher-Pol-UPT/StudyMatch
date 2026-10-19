export interface CalendarTask {
  id: string;
  title: string;
  date: Date | string;
  color?: string;
}

export interface AgendaStats {
  attendance: number;
  average: number;
  achievements: { earned: number; total: number };
}