import type { TFunction } from 'i18next';
import type { AttachmentData } from '../../types/chat';
import type { Sticker } from '../../types/enums';

interface Content { text: string; gifId?: string | null; sticker?: Sticker | null; attachments?: AttachmentData[] }

// a message with only files: "Photo", the file's name, or how many
export function filesOf(message: Content, t: TFunction) {
  const files = message.attachments ?? [];
  if (files.length > 1) return t('chats.files', { count: files.length });
  if (files.length === 1) return files[0].contentType.startsWith('image/') ? t('chats.photo') : files[0].name;
  return '';
}

// one line for a message: its text, "GIF", the sticker's name, or its files
export function lineOf(message: Content, t: TFunction) {
  if (message.text) return message.text;
  if (message.gifId) return t('chats.gif');
  if (message.sticker) return t('chats.stickerLine', { name: t(`stickers.${message.sticker}`) });
  return filesOf(message, t);
}
