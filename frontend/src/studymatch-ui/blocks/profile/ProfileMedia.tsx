import { Camera, ImagePlus, Palette, Trash2, Upload } from 'lucide-react';
import { useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { Avatar } from '../../components/avatar/Avatar';
import { Button } from '../../components/button/Button';
import { Dialog } from '../../components/overlay/Dialog';
import { Menu, type MenuEntry } from '../../components/overlay/Menu';
import { Popover } from '../../components/overlay/Popover';
import { useToast } from '../../components/overlay/Toast';
import { cx } from '../../utils/cx';
import { formatBytes } from '../../utils/shared';
import { ImageCropper, type CroppedImage } from './ImageCropper';
import './profile.css';

/** Edit Profile rule: JPG or PNG, up to 5 MB. */
export const PROFILE_IMAGE_TYPES = ['image/jpeg', 'image/png'];
export const PROFILE_IMAGE_MAX = 5 * 1024 * 1024;

export function validateProfileImage(file: File, maxSize = PROFILE_IMAGE_MAX): string | null {
  if (!PROFILE_IMAGE_TYPES.includes(file.type)) return 'Use a JPG or PNG image.';
  if (file.size > maxSize) return `This image is ${formatBytes(file.size)}. The limit is ${formatBytes(maxSize)}.`;
  return null;
}

// ─── Banner presets ────────────────────────────────────────
export type BannerPreset = 'violet' | 'sunrise' | 'fresh' | 'ink' | 'lavender' | 'sky' | 'peach' | 'mint';

export const BANNER_PRESETS: Record<BannerPreset, { label: string; background: string }> = {
  violet: { label: 'Violet', background: 'radial-gradient(60% 120% at 85% 0%, rgba(255,255,255,.28), transparent 60%), var(--sm-grad-brand)' },
  sunrise: { label: 'Sunrise', background: 'radial-gradient(60% 120% at 85% 0%, rgba(255,255,255,.3), transparent 60%), var(--sm-grad-sunrise)' },
  fresh: { label: 'Fresh', background: 'radial-gradient(60% 120% at 85% 0%, rgba(255,255,255,.3), transparent 60%), var(--sm-grad-fresh)' },
  ink: { label: 'Ink', background: 'radial-gradient(50% 120% at 90% 0%, rgba(122,107,255,.7), transparent 70%), radial-gradient(40% 100% at 0% 100%, rgba(42,159,214,.45), transparent 70%), #0d0b24' },
  lavender: { label: 'Lavender', background: 'radial-gradient(60% 120% at 90% 0%, #d6ceff, transparent 70%), var(--sm-tint-lavender)' },
  sky: { label: 'Sky', background: 'radial-gradient(60% 120% at 90% 0%, #bfe3ff, transparent 70%), var(--sm-tint-sky)' },
  peach: { label: 'Peach', background: 'radial-gradient(60% 120% at 90% 0%, #ffd2bb, transparent 70%), var(--sm-tint-peach)' },
  mint: { label: 'Mint', background: 'radial-gradient(60% 120% at 90% 0%, #b8edd5, transparent 70%), var(--sm-tint-mint)' },
};

export interface ProfileBannerProps {
  /** Banner photo. Takes priority over the preset. */
  src?: string | null;
  preset?: BannerPreset;
  /** Height in px (the width is fluid). */
  height?: number;
  children?: ReactNode;
  className?: string;
}

/** The wide cover at the top of a profile: a photo or a colour preset with a soft dot texture. */
export function ProfileBanner({ src, preset = 'violet', height = 220, children, className }: ProfileBannerProps) {
  const style: CSSProperties = { height, background: src ? `center / cover no-repeat url("${src}")` : BANNER_PRESETS[preset].background };
  return (
    <div className={cx('sm-banner-cover', !src && 'sm-banner-cover--preset', className)} style={style}>
      {children}
    </div>
  );
}

// ─── Shared: pick → crop ───────────────────────────────────
function useImagePicker(maxSize: number) {
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const toast = useToast();
  const element = (
    <input
      ref={input}
      type="file"
      accept={PROFILE_IMAGE_TYPES.join(',')}
      className="sm-visually-hidden"
      tabIndex={-1}
      aria-hidden
      onChange={(e) => {
        const f = e.target.files?.[0];
        e.target.value = '';
        if (!f) return;
        const error = validateProfileImage(f, maxSize);
        if (error) toast.danger(error, { description: 'Accepted: JPG or PNG, up to 5 MB.' });
        else setFile(f);
      }}
    />
  );
  return { open: () => input.current?.click(), file, clear: () => setFile(null), element };
}

// ─── Avatar uploader ───────────────────────────────────────
export interface AvatarUploaderProps {
  name: string;
  /** Current photo URL. Without it, the initials avatar is shown. */
  src?: string | null;
  /** Called with the cropped photo, or null when removed. */
  onChange: (image: CroppedImage | null) => void;
  size?: number;
  maxSize?: number;
  /** Ring colour around the avatar (match the surface behind it). */
  ring?: string;
  disabled?: boolean;
  className?: string;
}

/** Profile photo with a camera button: upload, crop to a circle, change or remove. */
export function AvatarUploader({ name, src, onChange, size = 120, maxSize = PROFILE_IMAGE_MAX, ring = 'var(--sm-white)', disabled, className }: AvatarUploaderProps) {
  const picker = useImagePicker(maxSize);
  const btn = (
    <button type="button" className="sm-avatar-up__btn" aria-label={src ? 'Change profile photo' : 'Add profile photo'} disabled={disabled}>
      <Camera size={Math.max(14, size * 0.14)} strokeWidth={2.25} />
    </button>
  );
  return (
    <span className={cx('sm-avatar-up', className)} style={{ width: size, height: size, ['--ring' as string]: ring } as CSSProperties}>
      <Avatar name={name} src={src ?? undefined} size={size} />
      {!disabled && (
        <span className="sm-avatar-up__slot">
          {src ? (
            <Menu
              trigger={btn}
              align="start"
              label="Profile photo"
              items={[
                { label: 'Upload a new photo', icon: Upload, onSelect: picker.open },
                { type: 'separator' },
                { label: 'Remove photo', icon: Trash2, danger: true, onSelect: () => onChange(null) },
              ]}
            />
          ) : (
            <span onClick={picker.open}>{btn}</span>
          )}
        </span>
      )}
      {picker.element}
      <Dialog open={!!picker.file} onClose={picker.clear} title="Position your photo" description="Drag to move, use the slider to zoom. JPG or PNG, up to 5 MB." size="sm">
        {picker.file && (
          <ImageCropper
            source={picker.file}
            aspect={1}
            shape="circle"
            outputWidth={512}
            applyLabel="Use photo"
            onCancel={picker.clear}
            onApply={(img) => {
              onChange(img);
              picker.clear();
            }}
          />
        )}
      </Dialog>
    </span>
  );
}

// ─── Banner uploader ───────────────────────────────────────
export interface BannerUploaderProps {
  src?: string | null;
  preset?: BannerPreset;
  /** New photo (cropped 3:1) or null when the photo is removed. */
  onImageChange: (image: CroppedImage | null) => void;
  onPresetChange?: (preset: BannerPreset) => void;
  height?: number;
  maxSize?: number;
  children?: ReactNode;
  className?: string;
}

/** Editable cover: upload and crop a 3:1 photo, or pick a colour preset. */
export function BannerUploader({ src, preset = 'violet', onImageChange, onPresetChange, height = 220, maxSize = PROFILE_IMAGE_MAX, children, className }: BannerUploaderProps) {
  const picker = useImagePicker(maxSize);
  const items: MenuEntry[] = [{ label: src ? 'Upload a new cover' : 'Upload a cover photo', icon: ImagePlus, onSelect: picker.open }];
  if (src) items.push({ type: 'separator' }, { label: 'Remove photo', icon: Trash2, danger: true, onSelect: () => onImageChange(null) });
  return (
    <ProfileBanner src={src} preset={preset} height={height} className={cx('sm-banner-cover--editable', className)}>
      <div className="sm-banner-cover__tools">
        {onPresetChange && (
          <Popover
            align="end"
            label="Cover colour"
            width={248}
            trigger={
              <Button size="sm" variant="white" icon={Palette}>
                Colour
              </Button>
            }
          >
            {(close) => (
              <div className="sm-preset-grid" role="radiogroup" aria-label="Cover colour">
                {(Object.keys(BANNER_PRESETS) as BannerPreset[]).map((p) => (
                  <button
                    key={p}
                    type="button"
                    role="radio"
                    aria-checked={!src && p === preset}
                    aria-label={BANNER_PRESETS[p].label}
                    title={BANNER_PRESETS[p].label}
                    style={{ background: BANNER_PRESETS[p].background }}
                    onClick={() => {
                      onPresetChange(p);
                      if (src) onImageChange(null);
                      close();
                    }}
                  />
                ))}
              </div>
            )}
          </Popover>
        )}
        <Menu
          trigger={
            <Button size="sm" variant="white" icon={Camera}>
              Cover
            </Button>
          }
          label="Cover photo"
          items={items}
        />
      </div>
      {children}
      {picker.element}
      <Dialog open={!!picker.file} onClose={picker.clear} title="Position your cover" description="It’s shown at 3:1, wider on large screens. JPG or PNG, up to 5 MB." size="lg">
        {picker.file && (
          <ImageCropper
            source={picker.file}
            aspect={3}
            shape="rect"
            outputWidth={1500}
            applyLabel="Use cover"
            onCancel={picker.clear}
            onApply={(img) => {
              onImageChange(img);
              picker.clear();
            }}
          />
        )}
      </Dialog>
    </ProfileBanner>
  );
}
