export const EnrolmentResult = ['ENROLLED', 'ALREADY_ENROLLED', 'ALREADY_PASSED', 'NOT_FOUND', 'INVALID_NUMBER', 'DUPLICATE'] as const;
export type EnrolmentResult = typeof EnrolmentResult[number];

// one per student sent; row only for the CSV, name only when the student exists
export interface EnrolmentResultData { row?: number; number?: number; name?: string; result: EnrolmentResult }

export interface EnrolStudentsRequest { classSection: string; studentNumbers: number[] }
