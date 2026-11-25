// a subject in the catalogue, as GET /subjects/ returns it
export interface SubjectData {
  id: number; code: string; name: string; ects: number;
  category: { id: number; name: string };
  degrees: { degreeId: number; degreeName: string; curricularYear: number; semester: number }[];
}
export interface DegreePlacementRequest { degreeId: number; curricularYear: number; semester: number }
export interface SubjectRequest {
  code: string; name: string; ects: number;
  // null when no category is chosen, so the backend answers VALIDATION_FAILED under the field instead of NOT_FOUND
  categoryId: number | null;
  degrees: DegreePlacementRequest[];
  edition: { academicYear: number; teacherNumbers: number[]; classSections?: string[] };
}
export interface TeacherData { number: number; name: string; institutionalEmail: string }
export interface AcademicYearPreviewData { nextStartYear: number }
