import { useTranslation } from 'react-i18next';
import { attachmentUrl } from '../../api/chats';
import { ChatFile, ChatFiles, ChatImage, ChatUpload, ChatUploads } from '../../studymatch-ui';
import type { AttachmentData } from '../../types/chat';
import { contentTypeOf, fileProblem, formatSize, useObjectUrl } from './attachmentFiles';

// the cards above the message box, with why one can't be sent
export function ComposerFiles({ files, onRemove }: { files: File[]; onRemove: (index: number) => void }) {
  const { t } = useTranslation();
  return (
    <ChatUploads label={t('chats.filesToSend')}>
      {files.map((file, index) => (
        // the same file can be added twice, so the place is part of the key
        <ComposerFile key={`${index}-${file.name}-${file.size}`} file={file} problem={fileProblem(file, index, t)} onRemove={() => onRemove(index)} />
      ))}
    </ChatUploads>
  );
}

function ComposerFile({ file, problem, onRemove }: { file: File; problem?: string; onRemove: () => void }) {
  const { t, i18n } = useTranslation();
  const type = contentTypeOf(file);
  const thumb = useObjectUrl(type.startsWith('image/') && !problem ? file : undefined);
  return (
    <ChatUpload name={file.name} contentType={type} sub={problem ?? formatSize(file.size, i18n.language)} thumbSrc={thumb}
      error={!!problem} removeLabel={t('chats.removeFile', { name: file.name })} onRemove={onRemove} />
  );
}

export interface OpenImage { src: string; name: string }

// a sent message's files: images to open bigger, the rest to download
export function MessageFiles({ chatId, messageId, attachments, onOpen }: { chatId: number; messageId: number; attachments: AttachmentData[]; onOpen: (image: OpenImage) => void }) {
  const { t, i18n } = useTranslation();
  const url = (file: AttachmentData) => attachmentUrl(chatId, messageId, file.id);
  const images = attachments.filter(file => file.contentType.startsWith('image/'));
  const others = attachments.filter(file => !file.contentType.startsWith('image/'));
  return (
    <ChatFiles
      images={images.map(file => (
        <ChatImage key={file.id} src={url(file)} alt={file.name} label={t('chats.openImage', { name: file.name })} onOpen={() => onOpen({ src: url(file), name: file.name })} />
      ))}
      files={others.map(file => (
        <ChatFile key={file.id} name={file.name} contentType={file.contentType} sizeLabel={formatSize(file.size, i18n.language)}
          href={url(file)} downloadLabel={t('chats.downloadFile', { name: file.name })} />
      ))} />
  );
}

// yours while they go up, from the files on this computer, with how much has
export function PendingFiles({ files, progress }: { files: File[]; progress?: number }) {
  const images = files.filter(file => contentTypeOf(file).startsWith('image/'));
  const others = files.filter(file => !contentTypeOf(file).startsWith('image/'));
  return (
    <ChatFiles images={images.map((file, index) => <PendingImage key={`${index}-${file.name}`} file={file} progress={progress} />)}
      files={others.map((file, index) => <PendingFile key={`${index}-${file.name}`} file={file} progress={progress} />)} />
  );
}

function PendingImage({ file, progress }: { file: File; progress?: number }) {
  const src = useObjectUrl(file);
  // the address is there right after the first render
  if (!src) return null;
  return <ChatImage src={src} alt={file.name} label={file.name} progress={progress} />;
}

function PendingFile({ file, progress }: { file: File; progress?: number }) {
  const { t, i18n } = useTranslation();
  return <ChatFile name={file.name} contentType={contentTypeOf(file)} sizeLabel={formatSize(file.size, i18n.language)} downloadLabel={t('chats.downloadFile', { name: file.name })} progress={progress} />;
}
