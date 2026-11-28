import type { NotificationTarget, NotificationType } from './enums';

// one notification (#144): a type to translate with its params, and the page it opens when target isn't null
export interface NotificationData {
  id: number;
  type: NotificationType;
  params: Record<string, unknown>;
  target: NotificationTarget | null;
  targetId: number | null;
  createdAt: string;
  read: boolean;
}
