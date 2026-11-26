import type { TFunction } from 'i18next';
import type { AttachmentData } from '../../types/chat';

interface Content { text: string; gifId?: string | null; attachments?: AttachmentData[] }

// a message with only files: "Photo", the file's name, or how many
export function filesOf(message: Content, t: TFunction) {
  const files = message.attachments ?? [];
  if (files.length > 1) return t('chats.files', { count: files.length });
  if (files.length === 1) return files[0].contentType.startsWith('image/') ? t('chats.photo') : files[0].name;
  return '';
}

// one line for a message: its text, or "GIF", or its files
export const lineOf = (message: Content, t: TFunction) => message.text || (message.gifId ? t('chats.gif') : filesOf(message, t));
