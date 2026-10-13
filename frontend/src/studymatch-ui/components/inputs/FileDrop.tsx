import { FileText, ImageIcon, UploadCloud, X } from 'lucide-react';
import { useEffect, useId, useRef, useState, type DragEvent, type ReactNode } from 'react';
import { cx } from '../../utils/cx';
import { formatBytes } from '../../utils/shared';
import './inputs.css';

export interface FileDropProps {
  label?: ReactNode;
  /** e.g. "image/png,image/jpeg" or ".csv". */
  accept?: string;
  /** Max size in bytes, e.g. 5 * 1024 * 1024 for a 5 MB banner. */
  maxSize?: number;
  multiple?: boolean;
  /** Valid files only. Invalid ones are listed with the reason. */
  onFiles: (files: File[]) => void;
  hint?: ReactNode;
  /** Show a preview for images. */
  preview?: boolean;
  disabled?: boolean;
  className?: string;
}

function matchesAccept(file: File, accept?: string) {
  if (!accept) return true;
  return accept.split(',').some((raw) => {
    const a = raw.trim().toLowerCase();
    if (a.startsWith('.')) return file.name.toLowerCase().endsWith(a);
    if (a.endsWith('/*')) return file.type.startsWith(a.slice(0, -1));
    return file.type === a;
  });
}

/** Drag-and-drop or click to choose (banner upload, CSV import). Validates type and size. */
export function FileDrop({ label = 'Drop a file here or click to browse', accept, maxSize, multiple, onFiles, hint, preview = true, disabled, className }: FileDropProps) {
  const id = useId();
  const input = useRef<HTMLInputElement>(null);
  const [over, setOver] = useState(false);
  const [files, setFiles] = useState<File[]>([]);
  const [errors, setErrors] = useState<string[]>([]);
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    const img = files.find((f) => f.type.startsWith('image/'));
    if (!preview || !img) {
      setUrl(null);
      return;
    }
    const u = URL.createObjectURL(img);
    setUrl(u);
    return () => URL.revokeObjectURL(u);
  }, [files, preview]);

  const take = (list: FileList | null) => {
    if (!list) return;
    const ok: File[] = [];
    const bad: string[] = [];
    Array.from(list)
      .slice(0, multiple ? undefined : 1)
      .forEach((f) => {
        if (!matchesAccept(f, accept)) bad.push(`${f.name}: this file type isn't supported.`);
        else if (maxSize && f.size > maxSize) bad.push(`${f.name} is ${formatBytes(f.size)}. The limit is ${formatBytes(maxSize)}.`);
        else ok.push(f);
      });
    setErrors(bad);
    setFiles(ok);
    if (ok.length) onFiles(ok);
  };

  const onDrop = (e: DragEvent) => {
    e.preventDefault();
    setOver(false);
    if (!disabled) take(e.dataTransfer.files);
  };

  return (
    <div className={cx('sm-drop-wrap', className)}>
      <label
        htmlFor={id}
        className={cx('sm-drop', over && 'sm-drop--over', disabled && 'sm-drop--disabled', errors.length > 0 && 'sm-drop--error')}
        onDragOver={(e) => {
          e.preventDefault();
          if (!disabled) setOver(true);
        }}
        onDragLeave={() => setOver(false)}
        onDrop={onDrop}
      >
        {url ? (
          <img className="sm-drop__preview" src={url} alt="Selected image preview" />
        ) : (
          <span className="sm-drop__icon" aria-hidden>
            <UploadCloud size={26} strokeWidth={2} />
          </span>
        )}
        <span className="sm-drop__label">{label}</span>
        {hint && <span className="sm-drop__hint">{hint}</span>}
        <input
          ref={input}
          id={id}
          type="file"
          className="sm-visually-hidden"
          accept={accept}
          multiple={multiple}
          disabled={disabled}
          onChange={(e) => {
            take(e.target.files);
            e.target.value = '';
          }}
        />
      </label>
      {files.length > 0 && (
        <ul className="sm-drop__files">
          {files.map((f) => (
            <li key={f.name}>
              {f.type.startsWith('image/') ? <ImageIcon size={16} aria-hidden /> : <FileText size={16} aria-hidden />}
              <span>{f.name}</span>
              <small>{formatBytes(f.size)}</small>
              <button type="button" aria-label={`Remove ${f.name}`} onClick={() => setFiles((xs) => xs.filter((x) => x !== f))}>
                <X size={14} strokeWidth={2.5} />
              </button>
            </li>
          ))}
        </ul>
      )}
      {errors.map((e) => (
        <p key={e} className="sm-drop__error" role="alert">
          {e}
        </p>
      ))}
    </div>
  );
}
