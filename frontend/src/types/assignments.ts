import type { AssignmentType } from './enums';

// two students (by number) who can't end up in the same group
export interface SeparationData { firstStudent: number; secondStudent: number }

export interface GroupingData { minSize: number; optimalSize: number; maxSize: number; sameClass: boolean; separations: SeparationData[] }

// weight is a whole percentage; no grouping means individual work
export interface AssignmentRequest { name: string; type: AssignmentType; date: string | null; weight: number; grouping: GroupingData | null }

export interface AssignmentData { id: number; name: string; type: AssignmentType; date: string; weight: number; grouping: GroupingData | null }
