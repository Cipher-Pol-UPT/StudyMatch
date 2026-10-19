import type { AccountStatus, EnrolmentStatus, StatuteType } from './enums';
import type { PersonalityProfileData } from './personalityProfile';
import type { StudentProfileData } from './studentProfile';

export interface PageData<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface DegreeData { id: number; name: string; curricularYears: number }
export interface StudentSummaryData {
  number: number; name: string; degree: string; curricularYear: number; attendance: number | null;
  averageGrade: number | null; personalityProfile: PersonalityProfileData | null; accountStatus: AccountStatus;
}
export interface CategoryLevelData { categoryId: number; category: string; level: number }
export interface EnrolmentRecordData {
  subjectCode: string; subjectName: string; ects: number; academicYear: number;
  classSection: string; status: EnrolmentStatus; finalGrade: number | null;
}
export interface StudentRecordData {
  profile: StudentProfileData; personalEmail: string; accountStatus: AccountStatus; statuteType: StatuteType | null;
  statuteEndDate: string | null; averageGrade: number | null; categoryLevels: CategoryLevelData[]; enrolments: EnrolmentRecordData[];
}
export interface EditionStudentData {
  number: number; name: string; classSection: string; personalityProfile: PersonalityProfileData | null;
  categoryLevel: number | null; attendance: number | null;
}
export interface SubjectEditionData {
  id: number; academicYear: number;
  subject: { id: number; code: string; name: string; ects: number; category: { id: number; name: string }; degrees: { degreeId: number; degreeName: string; curricularYear: number; semester: number }[] };
  teachers: { number: number; name: string }[]; classSections: string[];
}
