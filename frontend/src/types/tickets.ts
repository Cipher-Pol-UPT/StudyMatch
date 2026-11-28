import type { Role, TicketCategory, TicketStatus } from './enums';

// a row of my tickets (#362)
export interface TicketSummaryData {
  id: number;
  category: TicketCategory;
  subject: string;
  status: TicketStatus;
  createdAt: string;
  updatedAt: string;
}

// the author is null when a guest who contacted support wrote it
export interface TicketReplyData {
  id: number;
  authorNumber: number | null;
  authorName: string | null;
  authorRole: Role | null;
  text: string;
  createdAt: string;
}

// a ticket with its thread, oldest reply first
export interface TicketData extends TicketSummaryData {
  description: string;
  replies: TicketReplyData[];
}

export interface OpenTicketRequest {
  category: TicketCategory;
  subject: string;
  description: string;
}
