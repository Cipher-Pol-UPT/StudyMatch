// the pages of one subject edition, each also a menu item that starts by choosing the subject
export const SUBJECT_TABS = ['students', 'assignments'] as const;
export type SubjectTab = typeof SUBJECT_TABS[number];
