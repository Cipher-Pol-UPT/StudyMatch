export type ClassValue = string | false | null | undefined | 0;

export const cx = (...classes: ClassValue[]) => classes.filter(Boolean).join(' ');
