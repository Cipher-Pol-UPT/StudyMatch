// a subject in the catalogue, as GET /subjects/ returns it
export interface SubjectData {
  id: number; code: string; name: string; ects: number;
  category: { id: number; name: string };
  degrees: { degreeId: number; degreeName: string; curricularYear: number; semester: number }[];
}
export interface DegreePlacementRequest { degreeId: number; curricularYear: number; semester: number }
export interface SubjectRequest {
  code: string; name: string; ects: number; categoryId: number;
  degrees: DegreePlacementRequest[];
  edition: { academicYear: number; teacherNumbers: number[]; classSections?: string[] };
}
export interface TeacherData { number: number; name: string; institutionalEmail: string }
// startYear 2026 is the 2026/2027 academic year
export interface AcademicYearData { startYear: number }
