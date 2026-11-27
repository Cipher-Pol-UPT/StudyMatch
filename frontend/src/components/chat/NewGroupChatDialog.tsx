import { UsersRound } from 'lucide-react';
import { useCallback, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { createGroupChat, searchChatContacts } from '../../api/chats';
import { isBackendError, type FieldError } from '../../api/errors';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Banner, Button, ChatPeople, ChatPerson, ChatPicture, Chip, Dialog, ProgressBar, SearchField, TextField, useFormFeedback } from '../../studymatch-ui';
import {
  GROUP_CHAT_MAX_MEMBERS, GROUP_CHAT_MIN_OTHERS, GROUP_CHAT_NAME_MAX_LENGTH, GROUP_CHAT_PICTURE_MAX_BYTES,
  type ChatContactData, type ChatSummaryData,
} from '../../types/chat';
import { DataLoadState } from '../feedback/DataLoadState';

export interface NewGroupChatDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated: (chat: ChatSummaryData) => void;
}

const PICTURE_TYPES = ['image/png', 'image/jpeg'];

// a group chat you own: its name, an optional picture and 2 to 19 others picked from the same search as New message (#227)
export function NewGroupChatDialog({ open, onClose, onCreated }: NewGroupChatDialogProps) {
  const { t } = useTranslation();
  return (
    <Dialog open={open} onClose={onClose} title={t('chats.newGroupChat')} description={t('chats.newGroupChatDescription', { max: GROUP_CHAT_MAX_MEMBERS })}>
      {/* only there while open, so every time it starts empty */}
      <GroupChatForm onCancel={onClose} onCreated={onCreated} />
    </Dialog>
  );
}

function GroupChatForm({ onCancel, onCreated }: { onCancel: () => void; onCreated: (chat: ChatSummaryData) => void }) {
  const { t } = useTranslation();
  const [name, setName] = useState('');
  const [picture, setPicture] = useState<string | null>(null);
  const [picked, setPicked] = useState<ChatContactData[]>([]);
  // what's wrong before asking the backend, shown once Create was pressed
  const [checked, setChecked] = useState<FieldError[]>([]);
  // fewer than 2 others picked when Create was pressed
  const [tooFew, setTooFew] = useState(false);
  // the people the backend refused (GR9), marked in the list of picked ones
  const [refused, setRefused] = useState<number[]>([]);
  const feedback = useFormFeedback(checked, {
    NOT_ENOUGH_GROUP_CHAT_MEMBERS: 'memberNumbers', TOO_MANY_GROUP_CHAT_MEMBERS: 'memberNumbers',
    CANNOT_ADD_TO_GROUP_CHAT: 'memberNumbers', INVALID_GROUP_CHAT_PICTURE: 'picture',
  });
  // you count too
  const members = picked.length + 1;
  const full = members >= GROUP_CHAT_MAX_MEMBERS;
  const isPicked = (contact: ChatContactData) => picked.some(person => person.number === contact.number);

  const toggle = (contact: ChatContactData) => {
    setPicked(current => isPicked(contact) ? current.filter(person => person.number !== contact.number) : [...current, contact]);
    setTooFew(false);
    setRefused(current => current.filter(number => number !== contact.number));
  };

  const pickPicture = (file: File) => {
    setChecked(current => current.filter(error => error.field !== 'picture'));
    if (!PICTURE_TYPES.includes(file.type) || file.size > GROUP_CHAT_PICTURE_MAX_BYTES) {
      setChecked(current => [...current, { field: 'picture', code: 'INVALID' }]);
      return;
    }
    const reader = new FileReader();
    reader.onload = () => setPicture(reader.result as string);
    reader.readAsDataURL(file);
  };

  const create = (event: FormEvent) => {
    event.preventDefault();
    const blank = !name.trim();
    const few = picked.length < GROUP_CHAT_MIN_OTHERS;
    setChecked(current => [...current.filter(error => error.field !== 'name'), ...(blank ? [{ field: 'name', code: 'NOT_BLANK' }] : [])]);
    setTooFew(few);
    if (blank || few || checked.some(error => error.field === 'picture')) return;
    void feedback.submit(async () => {
      try {
        onCreated(await createGroupChat(name.trim(), picked.map(person => person.number), picture));
      } catch (error) {
        if (isBackendError(error) && error.code === 'CANNOT_ADD_TO_GROUP_CHAT') setRefused((error.params?.numbers as number[] | undefined) ?? []);
        throw error;
      }
    });
  };

  // a picture problem found here reads like the backend's
  const pictureError = feedback.fieldError('picture') && t('errors.INVALID_GROUP_CHAT_PICTURE');
  const refusedNames = picked.filter(person => refused.includes(person.number)).map(person => person.name);
  const membersError = refusedNames.length ? t('chats.cantBeAdded', { names: refusedNames.join(', ') })
    : tooFew ? t('errors.NOT_ENOUGH_GROUP_CHAT_MEMBERS', { min: GROUP_CHAT_MIN_OTHERS }) : feedback.fieldError('memberNumbers');

  return (
    <form className="sm-chat__group-form" onSubmit={create} noValidate>
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      <div className="sm-chat__group-name">
        <ChatPicture src={picture ?? undefined} label={t('chats.addPicture')} removeLabel={t('chats.removePicture')}
          onPick={pickPicture} onRemove={() => setPicture(null)} />
        <TextField label={t('chats.groupName')} placeholder={t('chats.groupNamePlaceholder')} value={name} maxLength={GROUP_CHAT_NAME_MAX_LENGTH}
          onChange={event => { setName(event.target.value); setChecked(current => current.filter(error => error.field !== 'name')); }}
          error={feedback.fieldError('name')} disabled={feedback.busy} autoFocus containerClassName="sm-chat__group-field" />
      </div>
      {pictureError && <p className="sm-chat__group-error" role="alert">{pictureError}</p>}

      {picked.length > 0 && (
        <div className="sm-chat__picked" role="list" aria-label={t('chats.pickedPeople')}>
          {picked.map(person => (
            <span role="listitem" key={person.number}>
              <Chip size="sm" className={refused.includes(person.number) ? 'sm-chip--refused' : undefined}
                onRemove={() => toggle(person)} removeLabel={t('chats.unpick', { name: person.name })}>{person.name}</Chip>
            </span>
          ))}
        </div>
      )}
      <div className="sm-chat__counter">
        <span>{t('chats.members')}</span>
        <span><b>{members}</b> / {GROUP_CHAT_MAX_MEMBERS}</span>
      </div>
      <ProgressBar size="sm" value={members} max={GROUP_CHAT_MAX_MEMBERS} label={t('chats.membersUsed', { count: members, max: GROUP_CHAT_MAX_MEMBERS })} />
      {membersError && <p className="sm-chat__group-error" role="alert">{membersError}</p>}
      {full && <p className="sm-caption" role="status">{t('chats.groupFull', { max: GROUP_CHAT_MAX_MEMBERS })}</p>}

      <PeopleSearch isPicked={isPicked} full={full} refused={refused} onToggle={toggle} />

      <div className="sm-dialog__actions">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>{t('common.cancel')}</Button>
        <Button type="submit" size="sm" icon={UsersRound} loading={feedback.busy}>{t('chats.createGroupChat')}</Button>
      </div>
    </form>
  );
}

interface PeopleSearchProps {
  isPicked: (contact: ChatContactData) => boolean;
  // nobody else fits, so only the picked ones can be clicked (to take them off)
  full: boolean;
  refused: number[];
  onToggle: (contact: ChatContactData) => void;
}

function PeopleSearch({ isPicked, full, refused, onToggle }: PeopleSearchProps) {
  const { t } = useTranslation();
  const [search, setSearch] = useState('');
  const query = new URLSearchParams({ ...(search.trim() ? { search: search.trim() } : {}), page: '0' }).toString();
  const load = useCallback(() => searchChatContacts(query), [query]);
  const contacts = useRemoteData(`chat-contacts?${query}`, load);
  const people = contacts.data;

  return (
    <>
      <SearchField label={t('chats.searchPeople')} placeholder={t('chats.searchPlaceholder')} value={search} onValueChange={setSearch} />
      {contacts.loading || contacts.error !== undefined || !people ? (
        <DataLoadState error={contacts.error} retry={contacts.retry} />
      ) : people.content.length === 0 ? (
        <p className="sm-caption" role="status">{t('chats.noOneFound')}</p>
      ) : (
        <ChatPeople label={t('chats.people')}>
          {people.content.map(contact => {
            const on = isPicked(contact);
            return (
              <ChatPerson key={contact.number} name={contact.name} picked={on} disabled={!on && full}
                subtitle={refused.includes(contact.number) ? t('chats.cantBeAddedOne') : t('chats.contactLine', { role: t(`roles.${contact.role}`), number: contact.number })}
                onPick={() => onToggle(contact)} />
            );
          })}
        </ChatPeople>
      )}
    </>
  );
}
