import { useCallback, useSyncExternalStore } from 'react';
import type { TFunction } from 'i18next';

// what the backend takes (GR10): 5 files a message, 10 MB each, of these types by their extension
export const MAX_FILES = 5;
export const MAX_FILE_MB = 10;
const TYPES: Record<string, string> = {
  png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif', webp: 'image/webp', pdf: 'application/pdf',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  txt: 'text/plain',
};
export const ACCEPT = Object.keys(TYPES).map(extension => `.${extension}`).join(',');

const extensionOf = (name: string) => name.includes('.') ? name.slice(name.lastIndexOf('.') + 1).toLowerCase() : '';
// by the extension, like the backend; what the browser says can be anything
export const contentTypeOf = (file: File) => TYPES[extensionOf(file.name)] ?? file.type;

// why a file added to the message box can't be sent, checked before anything goes up
export function fileProblem(file: File, index: number, t: TFunction) {
  if (index >= MAX_FILES) return t('chats.tooManyFiles', { max: MAX_FILES });
  if (!TYPES[extensionOf(file.name)]) return t('chats.fileTypeNotAllowed');
  if (file.size > MAX_FILE_MB * 1024 * 1024) return t('chats.fileTooBig', { max: MAX_FILE_MB });
  if (file.size === 0) return t('chats.fileEmpty');
  return undefined;
}

export function formatSize(bytes: number, language: string) {
  const megabytes = bytes >= 1024 * 1024;
  return new Intl.NumberFormat(language, {
    style: 'unit', unit: megabytes ? 'megabyte' : 'kilobyte', unitDisplay: 'short', maximumFractionDigits: megabytes ? 1 : 0,
  }).format(megabytes ? bytes / (1024 * 1024) : Math.max(1, bytes / 1024));
}

// a picked file as an address for <img src>, one per file while something shows it. The last one to go revokes it,
// since the address alone keeps the file in memory. StrictMode's second subscribe just makes a new one
const objectUrls = new Map<File, { url: string; users: number }>();

export function useObjectUrl(file: File | undefined) {
  const subscribe = useCallback(() => {
    if (!file) return () => undefined;
    let entry = objectUrls.get(file);
    if (!entry) {
      entry = { url: URL.createObjectURL(file), users: 0 };
      objectUrls.set(file, entry);
    }
    const used = entry;
    used.users++;
    return () => {
      used.users--;
      if (used.users === 0) {
        URL.revokeObjectURL(used.url);
        objectUrls.delete(file);
      }
    };
  }, [file]);
  // React reads it again right after subscribing, so the address shows once it's made
  return useSyncExternalStore(subscribe, () => (file ? objectUrls.get(file)?.url : undefined));
}
