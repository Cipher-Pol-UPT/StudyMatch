import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { isBackendError, type BackendError } from '../../api/errors';
import { updateMyProfile } from '../../api/students';
import { Card, EditProfileForm, ProfileHeader, type CroppedImage, type ProfileEdits } from '../../studymatch-ui';
import type { StudentProfileData } from '../../types/studentProfile';
import { toProfileView } from './toProfileView';

export interface EditProfileProps {
  profile: StudentProfileData;
  onSaved: (saved: StudentProfileData) => void;
  onCancel: () => void;
}

export function EditProfile({ profile, onSaved, onCancel }: EditProfileProps) {
  const { t } = useTranslation();
  const [banner, setBanner] = useState(profile.banner);
  const [pfp, setPfp] = useState(profile.pfp);
  const draft = { ...toProfileView(profile), bannerUrl: banner, avatarUrl: pfp };

  async function changeBanner(image: CroppedImage | null) {
    setBanner(await toDataUrl(image));
  }

  async function changePfp(image: CroppedImage | null) {
    setPfp(await toDataUrl(image));
  }

  async function save(edits: ProfileEdits) {
    try {
      onSaved(await updateMyProfile({ banner, pfp, ...edits }));
    } catch (error) {
      throw isBackendError(error) ? withFieldNamesOfTheForm(error) : error;
    }
  }

  return (
    <div className="profile-edit">
      <ProfileHeader
        profile={draft}
        viewer={{ showPrivate: true, canEdit: true }}
        editable
        onAvatarChange={changePfp}
        onBannerChange={changeBanner}
      />
      <Card title={t('ui.edit_profile')}>
        <EditProfileForm profile={draft} onSubmit={save} onCancel={onCancel} />
      </Card>
    </div>
  );
}

async function toDataUrl(image: CroppedImage | null): Promise<string | null> {
  if (image === null) {
    return null;
  }
  const dataUrl = await readAsDataUrl(image.blob);
  URL.revokeObjectURL(image.url);
  return dataUrl;
}

function readAsDataUrl(image: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(image);
  });
}

function withFieldNamesOfTheForm(error: BackendError): BackendError {
  return {
    ...error,
    fieldErrors: error.fieldErrors?.map(fieldError => ({
      ...fieldError,
      field: fieldError.field.replace(/\[\d*\]$/, ''),
    })),
  };
}
