import { FileText,ImageIcon,UploadCloud,X } from 'lucide-react';
import { useEffect,useId,useRef,useState,type DragEvent,type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
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
export function FileDrop({ label = i18n.t('ui.drop_a_file_here_or_click_to_browse'), accept, maxSize, multiple, onFiles, hint, preview = true, disabled, className }: FileDropProps) {
  useTranslation();
  const id = useId();
  const input = useRef<HTMLInputElement>(null);
  const [over, setOver] = useState(false);
  const [files, setFiles] = useState<File[]>([]);
  const [errors, setErrors] = useState<string[]>([]);
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => () => { if (url) URL.revokeObjectURL(url); }, [url]);



  const take = (list: FileList | null) => {
    if (!list) return;
    const ok: File[] = [];
    const bad: string[] = [];
    Array.from(list)
      .slice(0, multiple ? undefined : 1)
      .forEach((f) => {
        if (!matchesAccept(f, accept)) bad.push(i18n.t('ui.file_type', {name:f.name}));
        else if (maxSize && f.size > maxSize) bad.push(i18n.t('ui.file_size', {name:f.name,size:formatBytes(f.size),max:formatBytes(maxSize)}));
        else ok.push(f);
      });
    setErrors(bad);
    setFiles(ok);
    const image = ok.find(file => file.type.startsWith('image/'));
    setUrl(preview && image ? URL.createObjectURL(image) : null);
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
          <img className="sm-drop__preview" src={url} alt={i18n.t('ui.selected_image_preview')} />
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
              <button type="button" aria-label={i18n.t('ui.remove_item', {item:f.name})} onClick={() => { setFiles(xs => xs.filter(x => x !== f)); if (f.type.startsWith('image/')) setUrl(null); }}>
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
