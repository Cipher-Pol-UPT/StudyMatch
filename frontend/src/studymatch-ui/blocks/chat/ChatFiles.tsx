import { Check, Download, Maximize2, X } from 'lucide-react';
import type { ReactNode } from 'react';
import { IconButton } from '../../components/button/Button';
import { chatFileKind, chatFileTag, type ChatFileKind } from './fileKinds';

interface ChatFilePaperProps {
  kind: ChatFileKind;
  tag: string;
  size?: 'sm' | 'md';
}

// a little sheet of paper with a folded corner and the extension on it
export function ChatFilePaper({ kind, tag, size = 'md' }: ChatFilePaperProps) {
  return (
    <span className={`sm-paper sm-paper--${kind}${size === 'sm' ? ' sm-paper--sm' : ''}`} aria-hidden>
      <span className="sm-paper__lines"><i /><i /><i /></span>
      <span className="sm-paper__tag">{tag || '?'}</span>
    </span>
  );
}

export interface ChatFileProps {
  name: string;
  contentType: string;
  // already written for people, "1.4 MB"
  sizeLabel: string;
  // where it downloads from; none while it's still sending
  href?: string;
  downloadLabel: string;
  // 0 to 100 while it's sending
  progress?: number;
}

// a file in a message that isn't an image: its paper, name and size, and Download
export function ChatFile({ name, contentType, sizeLabel, href, downloadLabel, progress }: ChatFileProps) {
  return (
    <div className="sm-file">
      <ChatFilePaper kind={chatFileKind(contentType)} tag={chatFileTag(name)} />
      <span className="sm-file__text">
        <span className="sm-file__name" title={name}>{name}</span>
        <span className="sm-file__size">{sizeLabel}</span>
        {progress !== undefined && <ChatProgress value={progress} />}
      </span>
      {href && (
        <a className="sm-file__download" href={href} download={name} aria-label={downloadLabel} title={downloadLabel}>
          <Download size={16} aria-hidden />
        </a>
      )}
    </div>
  );
}

export interface ChatImageProps {
  src: string;
  alt: string;
  // "Open photo.png"; clicking shows it bigger
  label: string;
  onOpen?: () => void;
  progress?: number;
}

// an image in a message, a preview that opens bigger
export function ChatImage({ src, alt, label, onOpen, progress }: ChatImageProps) {
  return (
    <button type="button" className="sm-msg__image" aria-label={label} onClick={onOpen} disabled={!onOpen}>
      <img src={src} alt={alt} loading="lazy" />
      {onOpen && <span className="sm-msg__image-zoom" aria-hidden><Maximize2 size={14} /></span>}
      {progress !== undefined && <ChatProgress value={progress} />}
    </button>
  );
}

// a message's files: images side by side as a little mosaic, the other files under them
export function ChatFiles({ images, files }: { images: ReactNode[]; files: ReactNode[] }) {
  return (
    <div className="sm-msg__files">
      {images.length > 0 && <div className={`sm-msg__mosaic sm-msg__mosaic--${Math.min(images.length, 4)}`}>{images}</div>}
      {files}
    </div>
  );
}

function ChatProgress({ value }: { value: number }) {
  return <span className="sm-upload__progress" aria-hidden><i style={{ width: `${Math.max(4, Math.min(100, value))}%` }} /></span>;
}

export interface ChatUploadProps {
  name: string;
  contentType: string;
  // the size, or why it can't be sent
  sub: string;
  // the image itself, as a preview
  thumbSrc?: string;
  // can't be sent: the card is red and says why in sub
  error?: boolean;
  removeLabel: string;
  onRemove: () => void;
}

// a file added to the message box, before it's sent
export function ChatUpload({ name, contentType, sub, thumbSrc, error, removeLabel, onRemove }: ChatUploadProps) {
  const kind = chatFileKind(contentType);
  return (
    <div className={`sm-upload${error ? ' sm-upload--error' : ''}`} role="listitem">
      <span className="sm-upload__thumb">
        {thumbSrc && !error ? <img src={thumbSrc} alt="" /> : <ChatFilePaper kind={error ? 'file' : kind} tag={chatFileTag(name)} size="sm" />}
        {!error && <span className="sm-upload__ok" aria-hidden><Check size={10} strokeWidth={3} /></span>}
      </span>
      <span className="sm-upload__text">
        <span className="sm-upload__name" title={name}>{name}</span>
        <span className="sm-upload__sub" role={error ? 'alert' : undefined}>{sub}</span>
      </span>
      <IconButton icon={X} label={removeLabel} size="sm" variant="ghost" className="sm-upload__x" onClick={onRemove} />
    </div>
  );
}

// the cards above the message box
export function ChatUploads({ label, children }: { label: string; children: ReactNode }) {
  return <div className="sm-composer__files" role="list" aria-label={label}>{children}</div>;
}

