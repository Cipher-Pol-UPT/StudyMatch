import { Minus,Plus,RotateCcw } from 'lucide-react';
import { useCallback,useEffect,useLayoutEffect,useRef,useState,type KeyboardEvent,type PointerEvent,type WheelEvent } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { Button } from '../../components/button/Button';
import { cx } from '../../utils/cx';
import './profile.css';

export interface CroppedImage {
  blob: Blob;
  /** Object URL for an instant preview. Revoke it when you no longer need it. */
  url: string;
  width: number;
  height: number;
}

export interface ImageCropperProps {
  /** The picked file, or an image URL. */
  source: File | string;
  /** Width ÷ height of the result. 1 for avatars, 3 for banners. */
  aspect?: number;
  /** Circle mask for avatars, rectangle for banners. */
  shape?: 'circle' | 'rect';
  /** Pixel width of the exported image (height follows the aspect). */
  outputWidth?: number;
  /** JPEG quality 0–1. */
  quality?: number;
  maxZoom?: number;
  onApply: (image: CroppedImage) => void;
  onCancel?: () => void;
  applyLabel?: string;
  className?: string;
}

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v));

/**
 * Drag to reposition, zoom with the slider, the wheel or +/−, arrows to nudge.
 * Exports a JPEG through a canvas: no dependencies, no upload until you decide.
 */
export function ImageCropper({ source, aspect = 1, shape = 'circle', outputWidth = 512, quality = 0.9, maxZoom = 3, onApply, onCancel, applyLabel = i18n.t('ui.apply'), className }: ImageCropperProps) {
  useTranslation();
  const frame = useRef<HTMLDivElement>(null);
  const [src, setSrc] = useState<string | null>(null);
  const [img, setImg] = useState<HTMLImageElement | null>(null);
  const [box, setBox] = useState({ w: 0, h: 0 });
  const [zoom, setZoom] = useState(1);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [busy, setBusy] = useState(false);
  const drag = useRef<{ x: number; y: number; ox: number; oy: number } | null>(null);

  useEffect(() => {
    const url = typeof source === 'string' ? source : URL.createObjectURL(source);
    const image = new Image();
    image.crossOrigin = 'anonymous';
    image.onload = () => { setSrc(url); setZoom(1); setOffset({ x: 0, y: 0 }); setImg(image); };
    image.src = url;
    return () => {
      if (typeof source !== 'string') URL.revokeObjectURL(url);
    };
  }, [source]);

  useLayoutEffect(() => {
    const el = frame.current;
    if (!el) return;
    const measure = () => setBox({ w: el.clientWidth, h: el.clientWidth / aspect });
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, [aspect]);

  const base = img && box.w ? Math.max(box.w / img.naturalWidth, box.h / img.naturalHeight) : 1;
  const scale = base * zoom;
  const dispW = img ? img.naturalWidth * scale : 0;
  const dispH = img ? img.naturalHeight * scale : 0;

  const bound = useCallback(
    (o: { x: number; y: number }, s = scale) => {
      if (!img) return o;
      const mx = Math.max(0, (img.naturalWidth * s - box.w) / 2);
      const my = Math.max(0, (img.naturalHeight * s - box.h) / 2);
      return { x: clamp(o.x, -mx, mx), y: clamp(o.y, -my, my) };
    },
    [img, box, scale],
  );

  const setZoomTo = (z: number) => {
    const next = clamp(z, 1, maxZoom);
    setZoom(next);
    setOffset((o) => bound({ x: (o.x * next) / zoom, y: (o.y * next) / zoom }, base * next));
  };

  const onDown = (e: PointerEvent<HTMLDivElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    drag.current = { x: e.clientX, y: e.clientY, ox: offset.x, oy: offset.y };
  };
  const onMove = (e: PointerEvent<HTMLDivElement>) => {
    if (!drag.current) return;
    setOffset(bound({ x: drag.current.ox + e.clientX - drag.current.x, y: drag.current.oy + e.clientY - drag.current.y }));
  };
  const onUp = () => {
    drag.current = null;
  };
  const onWheel = (e: WheelEvent<HTMLDivElement>) => setZoomTo(zoom - e.deltaY * 0.0015);
  const onKey = (e: KeyboardEvent<HTMLDivElement>) => {
    const step = e.shiftKey ? 20 : 6;
    const moves: Record<string, [number, number]> = { ArrowLeft: [step, 0], ArrowRight: [-step, 0], ArrowUp: [0, step], ArrowDown: [0, -step] };
    if (moves[e.key]) {
      e.preventDefault();
      setOffset((o) => bound({ x: o.x + moves[e.key][0], y: o.y + moves[e.key][1] }));
    } else if (e.key === '+' || e.key === '=') setZoomTo(zoom + 0.1);
    else if (e.key === '-') setZoomTo(zoom - 0.1);
  };

  const apply = async () => {
    if (!img) return;
    setBusy(true);
    const outW = outputWidth;
    const outH = Math.round(outputWidth / aspect);
    const canvas = document.createElement('canvas');
    canvas.width = outW;
    canvas.height = outH;
    const ctx = canvas.getContext('2d')!;
    ctx.imageSmoothingQuality = 'high';
    const left = (box.w - dispW) / 2 + offset.x;
    const top = (box.h - dispH) / 2 + offset.y;
    ctx.drawImage(img, -left / scale, -top / scale, box.w / scale, box.h / scale, 0, 0, outW, outH);
    canvas.toBlob(
      (blob) => {
        setBusy(false);
        if (blob) onApply({ blob, url: URL.createObjectURL(blob), width: outW, height: outH });
      },
      'image/jpeg',
      quality,
    );
  };

  return (
    <div className={cx('sm-cropper', className)}>
      <div
        ref={frame}
        className={cx('sm-cropper__frame', shape === 'circle' && 'sm-cropper__frame--circle')}
        style={{ aspectRatio: String(aspect) }}
        onPointerDown={onDown}
        onPointerMove={onMove}
        onPointerUp={onUp}
        onPointerCancel={onUp}
        onWheel={onWheel}
        onKeyDown={onKey}
        tabIndex={0}
        role="application"
        aria-label={i18n.t('ui.image_position_drag_or_use_the_arrow_keys_to_move_plus_and_minus_')}
      >
        {src && img && (
          <img
            src={src}
            alt=""
            draggable={false}
            className="sm-cropper__img"
            style={{ width: dispW, height: dispH, transform: `translate(calc(-50% + ${offset.x}px), calc(-50% + ${offset.y}px))` }}
          />
        )}
        <span className="sm-cropper__mask" aria-hidden />
        <span className="sm-cropper__grid" aria-hidden />
      </div>
      <div className="sm-cropper__zoom">
        <button type="button" onClick={() => setZoomTo(zoom - 0.2)} aria-label={i18n.t('ui.zoom_out')} disabled={zoom <= 1}>
          <Minus size={16} strokeWidth={2.5} />
        </button>
        <input type="range" min={1} max={maxZoom} step={0.01} value={zoom} onChange={(e) => setZoomTo(Number(e.target.value))} aria-label={i18n.t('ui.zoom')} className="sm-range" style={{ ['--p' as string]: `${((zoom - 1) / (maxZoom - 1)) * 100}%` }} />
        <button type="button" onClick={() => setZoomTo(zoom + 0.2)} aria-label={i18n.t('ui.zoom_in')} disabled={zoom >= maxZoom}>
          <Plus size={16} strokeWidth={2.5} />
        </button>
        <button
          type="button"
          onClick={() => {
            setZoom(1);
            setOffset({ x: 0, y: 0 });
          }}
          aria-label={i18n.t('ui.reset_position')}
        >
          <RotateCcw size={15} strokeWidth={2.25} />
        </button>
      </div>
      <div className="sm-cropper__actions">
        {onCancel && (
          <Button variant="secondary" onClick={onCancel}>
             {i18n.t('ui.cancel')} </Button>
        )}
        <Button onClick={apply} loading={busy} disabled={!img}>
          {applyLabel}
        </Button>
      </div>
    </div>
  );
}
