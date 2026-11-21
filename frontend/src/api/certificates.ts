import type { ExportCertificateRequest, VerifiedCertificateData } from '../types/certificates';
import { download, request } from './client';

export const exportCertificate = (certificate: ExportCertificateRequest) => download('/achievements/certificate', certificate);

// public, works without a session
export const verifyCertificate = (code: string) =>
  request<VerifiedCertificateData>('GET', `/achievements/certificates/${encodeURIComponent(code)}`);
