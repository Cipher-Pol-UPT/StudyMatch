import type { OpenTicketRequest, TicketData, TicketSummaryData } from '../types/tickets';
import { request } from './client';

// Account goes to the Admin queue, the rest to Staff. Works before the personality test too
export const openTicket = (ticket: OpenTicketRequest) => request<TicketSummaryData>('POST', '/tickets/', ticket);
// the last one replied to first
export const getMyTickets = () => request<TicketSummaryData[]>('GET', '/tickets/mine');
export const getTicket = (id: number) => request<TicketData>('GET', `/tickets/${id}`);
// a Resolved ticket goes back to Open; answers with the whole thread
export const replyToTicket = (id: number, text: string) => request<TicketData>('POST', `/tickets/${id}/replies`, { text });
