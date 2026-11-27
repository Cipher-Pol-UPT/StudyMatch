// what a file is, by its content type: each kind has its own colour, like a pile of real papers
export type ChatFileKind = 'image' | 'pdf' | 'doc' | 'sheet' | 'slides' | 'text' | 'file';

export function chatFileKind(contentType: string): ChatFileKind {
  if (contentType.startsWith('image/')) return 'image';
  if (contentType === 'application/pdf') return 'pdf';
  if (contentType.includes('wordprocessingml')) return 'doc';
  if (contentType.includes('spreadsheetml')) return 'sheet';
  if (contentType.includes('presentationml')) return 'slides';
  if (contentType.startsWith('text/')) return 'text';
  return 'file';
}

// the extension shown on the paper, "PDF" or "DOCX"
export const chatFileTag = (name: string) => (name.includes('.') ? name.slice(name.lastIndexOf('.') + 1) : '').slice(0, 4).toUpperCase();
