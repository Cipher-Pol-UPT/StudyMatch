import { Camera,ImagePlus,Palette,Trash2,Upload } from 'lucide-react';
import { useRef,useState,type CSSProperties,type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { Avatar } from '../../components/avatar/Avatar';
import { Button } from '../../components/button/Button';
import { Dialog } from '../../components/overlay/Dialog';
import { Menu,type MenuEntry } from '../../components/overlay/Menu';
import { Popover } from '../../components/overlay/Popover';
import { useToast } from '../../components/overlay/useToast';
import { cx } from '../../utils/cx';
import { ImageCropper,type CroppedImage } from './ImageCropper';
import './profile.css';
import { BANNER_PRESETS,DEFAULT_IMAGE_MAX,PROFILE_IMAGE_TYPES,validateProfileImage } from './ProfileMedia.shared';

export type BannerPreset = 'violet' | 'sunrise' | 'fresh' | 'ink' | 'lavender' | 'sky' | 'peach' | 'mint';

export interface ProfileBannerProps {
  src?: string | null;
  preset?: BannerPreset;
  height?: number;
  children?: ReactNode;
  className?: string;
}

export function ProfileBanner({ src, preset = 'violet', height = 220, children, className }: ProfileBannerProps) {
  useTranslation();
  const style: CSSProperties = { height, background: src ? `center / cover no-repeat url("${src}")` : BANNER_PRESETS[preset].background };
  return (
    <div className={cx('sm-banner-cover', !src && 'sm-banner-cover--preset', className)} style={style}>
      {children}
    </div>
  );
}

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
        if (error) toast.danger(error, { get description() { return i18n.t('ui.select_an_image_to_preview'); } });
        else setFile(f);
      }}
    />
  );
  return { open: () => input.current?.click(), file, clear: () => setFile(null), element };
}

export interface AvatarUploaderProps {
  name: string;
  src?: string | null;
  onChange: (image: CroppedImage | null) => void;
  size?: number;
  maxSize?: number;
  ring?: string;
  disabled?: boolean;
  className?: string;
}

export function AvatarUploader({ name, src, onChange, size = 120, maxSize = DEFAULT_IMAGE_MAX, ring = 'var(--sm-white)', disabled, className }: AvatarUploaderProps) {
  useTranslation();
  const picker = useImagePicker(maxSize);
  const btn = (
    <button type="button" className="sm-avatar-up__btn" aria-label={src ? i18n.t('ui.change_profile_photo') : i18n.t('ui.add_profile_photo')} disabled={disabled}>
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
              label={i18n.t('ui.profile_photo')}
              items={[
                { get label() { return i18n.t('ui.upload_a_new_photo'); }, icon: Upload, onSelect: picker.open },
                { type: 'separator' },
                { get label() { return i18n.t('ui.remove_photo'); }, icon: Trash2, danger: true, onSelect: () => onChange(null) },
              ]}
            />
          ) : (
            <span onClick={picker.open}>{btn}</span>
          )}
        </span>
      )}
      {picker.element}
      <Dialog open={!!picker.file} onClose={picker.clear} title={i18n.t('ui.position_your_photo')} description={i18n.t('ui.drag_to_move_and_use_the_slider_to_zoom')} size="sm">
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

export interface BannerUploaderProps {
  src?: string | null;
  preset?: BannerPreset;
  onImageChange: (image: CroppedImage | null) => void;
  onPresetChange?: (preset: BannerPreset) => void;
  height?: number;
  maxSize?: number;
  children?: ReactNode;
  className?: string;
}

export function BannerUploader({ src, preset = 'violet', onImageChange, onPresetChange, height = 220, maxSize = DEFAULT_IMAGE_MAX, children, className }: BannerUploaderProps) {
  useTranslation();
  const picker = useImagePicker(maxSize);
  const items: MenuEntry[] = [{ label: src ? i18n.t('ui.upload_a_new_cover') : i18n.t('ui.upload_a_cover_photo'), icon: ImagePlus, onSelect: picker.open }];
  if (src) items.push({ type: 'separator' }, { get label() { return i18n.t('ui.remove_photo'); }, icon: Trash2, danger: true, onSelect: () => onImageChange(null) });
  return (
    <ProfileBanner src={src} preset={preset} height={height} className={cx('sm-banner-cover--editable', className)}>
      <div className="sm-banner-cover__tools">
        {onPresetChange && (
          <Popover
            align="end"
            label={i18n.t('ui.cover_colour')}
            width={248}
            trigger={
              <Button size="sm" variant="white" icon={Palette}>
                 {i18n.t('ui.colour')} </Button>
            }
          >
            {(close) => (
              <div className="sm-preset-grid" role="radiogroup" aria-label={i18n.t('ui.cover_colour')}>
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
               {i18n.t('ui.cover')} </Button>
          }
          label={i18n.t('ui.cover_photo')}
          items={items}
        />
      </div>
      {children}
      {picker.element}
      <Dialog open={!!picker.file} onClose={picker.clear} title={i18n.t('ui.position_your_cover')} description={i18n.t('ui.drag_to_position_the_cover')} size="lg">
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
