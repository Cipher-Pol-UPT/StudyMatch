import type { GroupStatus, Language } from './enums';

export interface GroupMemberData { number: number; name: string; classSection: string }

// one generated group; sharedLanguages empty means its members have no language in common (GR4)
export interface GroupPreviewData { number: number; size: number; sharedLanguages: Language[]; members: GroupMemberData[] }

// what generating will do: groups is 0 when the class can't be split, searchMillis is how long the algorithm searches
export interface GroupGenerationData { students: number; groups: number; optimalSize: number; searchMillis: number }

export type { GroupStatus };
