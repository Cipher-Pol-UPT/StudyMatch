import { vi } from 'vitest';

// every role's layout has the notification bell (#145), which asks for the notifications as soon as it shows. By
// default there are none, so the other tests' fake backends don't get its requests; tests/notifications.test.tsx
// gives them its own answers
vi.mock('../src/api/notifications', () => ({
  getNotifications: vi.fn(async () => ({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })),
  getUnreadNotifications: vi.fn(async () => 0),
  markNotificationRead: vi.fn(async () => undefined),
  markAllNotificationsRead: vi.fn(async () => undefined),
}));
