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
  const draft = { ...toProfileView(profile), bannerUrl: banner };

  async function changeBanner(image: CroppedImage | null) {
    if (image === null) {
      setBanner(null);
      return;
    }
    setBanner(await readAsDataUrl(image.blob));
    URL.revokeObjectURL(image.url);
  }

  async function save(edits: ProfileEdits) {
    try {
      onSaved(await updateMyProfile({ banner, ...edits }));
    } catch (error) {
      throw isBackendError(error) ? withFieldNamesOfTheForm(error) : error;
    }
  }

  return (
    <div className="profile-edit">
      <ProfileHeader profile={draft} viewer={{ showPrivate: true, canEdit: true }} editable onBannerChange={changeBanner} />
      <Card title={t('ui.edit_profile')}>
        <EditProfileForm profile={draft} onSubmit={save} onCancel={onCancel} />
      </Card>
    </div>
  );
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
