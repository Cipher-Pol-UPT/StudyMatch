import type { AssignmentType, EnrolmentStatus } from './enums';

// one column per assignment, weight as a whole percentage
export interface GradeColumn { id: number; name: string; type: AssignmentType; weight: number }

// grades by assignment id, a missing one isn't graded yet; finalGrade only once every assignment is graded
export interface GradeRow { number: number; name: string; classSection: string; grades: Record<string, number>; finalGrade: number | null; status: EnrolmentStatus }

// weightsComplete false means grades save but final grades wait until the weights reach 100%
export interface GradeSheetData { assignments: GradeColumn[]; students: GradeRow[]; weightsComplete: boolean }

export interface GradeResultData { student: GradeRow; weightsComplete: boolean }
