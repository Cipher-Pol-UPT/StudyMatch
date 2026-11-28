import type { StatusTone } from '../../studymatch-ui';
import type { TicketStatus } from '../../types/enums';

export const STATUS_TONE: Record<TicketStatus, StatusTone> = { OPEN: 'info', IN_PROGRESS: 'warning', RESOLVED: 'success' };

// the backend's local date-time, shown in the reader's language
export const formatWhen = (value: string, language: string) =>
  new Intl.DateTimeFormat(language, { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' }).format(new Date(value));
