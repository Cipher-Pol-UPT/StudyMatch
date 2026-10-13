export type ClassValue = string | false | null | undefined | 0;

/** Joins truthy class names. */
export const cx = (...classes: ClassValue[]) => classes.filter(Boolean).join(' ');
