// attendance is null before the first lesson and for a student with a statute (not applicable)
export interface AttendanceRow { number: number; name: string; absent: boolean; justified: boolean; attendance: number | null; statute: boolean }

// recorded false means nobody registered this lesson yet, so everyone shows as present
export interface AttendanceSheetData { classSection: string; startsAt: string; recorded: boolean; students: AttendanceRow[] }

// anyone not in absences was present, so an empty list is fine
export interface AttendanceRequest { classSection: string; startsAt: string; absences: { studentNumber: number; justified: boolean }[] }
