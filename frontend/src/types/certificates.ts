import type { Achievement } from './enums';

export interface ExportCertificateRequest { achievements: Achievement[]; language: 'en' | 'pt' }
// the figures saved the first time the achievement went on a certificate
export interface CertificateEvidenceData {
  roomsHeld: number; studentsTutored: number; averageRating: number | null; subject: string | null; recordedAt: string;
}
export interface VerifiedAchievementData { achievement: Achievement; earnedAt: string; evidence: CertificateEvidenceData | null }
export interface VerifiedCertificateData {
  record: boolean; studentName: string; studentNumber: number; degree: string | null;
  issuedAt: string | null; achievements: VerifiedAchievementData[];
}
