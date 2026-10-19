import type { FieldError } from '../api/errors';

export interface DegreeData {
  id: number;
  name: string;
  curricularYears: number;
}

export interface StudentRegistration {
  name: string;
  personalEmail: string;
  degreeProgramme: string;
  curricularYear: number | null;
}

export interface RegisteredStudent {
  number: number;
  name: string;
  institutionalEmail: string;
  degreeProgramme: string;
  curricularYear: number;
  emailSent: boolean;
}

export interface ImportPreviewRow {
  row: number;
  student: StudentRegistration;
  errors: FieldError[];
}

export type ImportStatus = 'CREATED' | 'REJECTED' | 'EMAIL_NOT_SENT';

export interface ImportResultRow {
  row: number;
  status: ImportStatus;
  number: number | null;
  institutionalEmail: string | null;
  errors: FieldError[];
}
