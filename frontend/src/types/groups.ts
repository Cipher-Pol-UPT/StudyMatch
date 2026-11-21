import type { GroupStatus, Language } from './enums';

export interface GroupMemberData { number: number; name: string; classSection: string }

// one generated group; sharedLanguages empty means its members have no language in common (GR4)
export interface GroupPreviewData { number: number; size: number; sharedLanguages: Language[]; members: GroupMemberData[] }

export type { GroupStatus };
