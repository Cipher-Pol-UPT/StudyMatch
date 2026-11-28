import { Check } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { changeChatPicture, renameChat } from '../../api/chats';
import type { FieldError } from '../../api/errors';
import { Banner, Button, ChatPicture, Dialog, TextField, useFormFeedback } from '../../studymatch-ui';
import { GROUP_CHAT_NAME_MAX_LENGTH, GROUP_CHAT_PICTURE_MAX_BYTES } from '../../types/chat';

const PICTURE_TYPES = ['image/png', 'image/jpeg'];

export interface EditGroupChatDialogProps {
  open: boolean;
  chatId: number;
  name: string;
  // the picture it has now, if any
  pictureSrc?: string;
  // only the owner changes the picture
  owner: boolean;
  onClose: () => void;
  // what was saved: the new name, and the picture as a data URL or null when it changed
  onSaved: (changes: { name: string; picture?: string | null }) => void;
}

// a group chat's name, which any member changes, and its picture, which only the owner does (#235)
export function EditGroupChatDialog({ open, onClose, ...form }: EditGroupChatDialogProps) {
  const { t } = useTranslation();
  return (
    <Dialog open={open} onClose={onClose} title={t('chats.editGroupChat')} description={form.owner ? t('chats.editGroupChatOwner') : t('chats.editGroupChatMember')}>
      {/* only there while open, so it starts from what the chat has now */}
      <EditForm {...form} onCancel={onClose} />
    </Dialog>
  );
}

function EditForm({ chatId, name: current, pictureSrc, owner, onCancel, onSaved }: Omit<EditGroupChatDialogProps, 'open' | 'onClose'> & { onCancel: () => void }) {
  const { t } = useTranslation();
  const [name, setName] = useState(current);
  // undefined while it's the one it has, null once it's taken off
  const [picture, setPicture] = useState<string | null>();
  const [checked, setChecked] = useState<FieldError[]>([]);
  const feedback = useFormFeedback(checked, { INVALID_GROUP_CHAT_PICTURE: 'picture' });

  const pickPicture = (file: File) => {
    setChecked(errors => errors.filter(error => error.field !== 'picture'));
    if (!PICTURE_TYPES.includes(file.type) || file.size > GROUP_CHAT_PICTURE_MAX_BYTES) {
      setChecked(errors => [...errors, { field: 'picture', code: 'INVALID' }]);
      return;
    }
    const reader = new FileReader();
    reader.onload = () => setPicture(reader.result as string);
    reader.readAsDataURL(file);
  };

  const save = (event: FormEvent) => {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      setChecked(errors => [...errors.filter(error => error.field !== 'name'), { field: 'name', code: 'NOT_BLANK' }]);
      return;
    }
    if (checked.some(error => error.field === 'picture')) return;
    const renamed = trimmed !== current;
    if (!renamed && picture === undefined) return onCancel();
    void feedback.submit(async () => {
      if (renamed) await renameChat(chatId, trimmed);
      if (picture !== undefined) await changeChatPicture(chatId, picture);
      onSaved(picture === undefined ? { name: trimmed } : { name: trimmed, picture });
    });
  };

  // a picture problem found here reads like the backend's
  const pictureError = feedback.fieldError('picture') && t('errors.INVALID_GROUP_CHAT_PICTURE');
  const shown = picture === undefined ? pictureSrc : picture ?? undefined;

  return (
    <form className="sm-chat__group-form" onSubmit={save} noValidate>
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      <div className="sm-chat__group-name">
        {owner && (
          <ChatPicture src={shown} label={t(shown ? 'chats.changePicture' : 'chats.addPicture')} removeLabel={t('chats.removePicture')}
            onPick={pickPicture} onRemove={() => { setPicture(null); setChecked(errors => errors.filter(error => error.field !== 'picture')); }} />
        )}
        <TextField label={t('chats.groupName')} value={name} maxLength={GROUP_CHAT_NAME_MAX_LENGTH}
          onChange={event => { setName(event.target.value); setChecked(errors => errors.filter(error => error.field !== 'name')); }}
          error={feedback.fieldError('name')} disabled={feedback.busy} autoFocus containerClassName="sm-chat__group-field" />
      </div>
      {pictureError && <p className="sm-chat__group-error" role="alert">{pictureError}</p>}

      <div className="sm-dialog__actions">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>{t('common.cancel')}</Button>
        <Button type="submit" size="sm" icon={Check} loading={feedback.busy}>{t('common.save')}</Button>
      </div>
    </form>
  );
}
