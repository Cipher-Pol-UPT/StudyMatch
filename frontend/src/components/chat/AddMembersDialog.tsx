import { UserPlus } from 'lucide-react';
import { useCallback, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { addChatMembers, getChatMembers } from '../../api/chats';
import { isBackendError } from '../../api/errors';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Banner, Button, Dialog, useFormFeedback } from '../../studymatch-ui';
import { GROUP_CHAT_MAX_MEMBERS, type ChatContactData } from '../../types/chat';
import { DataLoadState } from '../feedback/DataLoadState';
import { PeoplePicker } from './NewGroupChatDialog';

export interface AddMembersDialogProps {
  open: boolean;
  chatId: number;
  name: string;
  onClose: () => void;
  // the members after adding, the new ones included
  onAdded: (members: ChatContactData[], added: ChatContactData[]) => void;
}

// add people to a group chat, any member can (#229)
export function AddMembersDialog({ open, chatId, name, onClose, onAdded }: AddMembersDialogProps) {
  const { t } = useTranslation();
  return (
    <Dialog open={open} onClose={onClose} title={t('chats.addMembersTitle', { name })} description={t('chats.addMembersDescription')}>
      {/* only there while open: it starts empty and asks who's in it each time, nothing is pushed when someone else adds people */}
      <AddMembersForm chatId={chatId} onCancel={onClose} onAdded={onAdded} />
    </Dialog>
  );
}

function AddMembersForm({ chatId, onCancel, onAdded }: { chatId: number; onCancel: () => void; onAdded: AddMembersDialogProps['onAdded'] }) {
  const { t } = useTranslation();
  const load = useCallback(() => getChatMembers(chatId), [chatId]);
  const members = useRemoteData(`chat-members-adding:${chatId}`, load);
  const [picked, setPicked] = useState<ChatContactData[]>([]);
  // the people the backend refused (GR9), marked in the list
  const [refused, setRefused] = useState<number[]>([]);
  const feedback = useFormFeedback([], { TOO_MANY_GROUP_CHAT_MEMBERS: 'memberNumbers', CANNOT_ADD_TO_GROUP_CHAT: 'memberNumbers' });

  if (members.loading || members.error !== undefined || !members.data) return <DataLoadState error={members.error} retry={members.retry} />;

  const inIt = new Set(members.data.map(member => member.number));
  const count = members.data.length + picked.length;
  const isPicked = (contact: ChatContactData) => picked.some(person => person.number === contact.number);
  const toggle = (contact: ChatContactData) => {
    setPicked(current => isPicked(contact) ? current.filter(person => person.number !== contact.number) : [...current, contact]);
    setRefused(current => current.filter(number => number !== contact.number));
  };

  const add = (event: FormEvent) => {
    event.preventDefault();
    if (!picked.length) return;
    void feedback.submit(async () => {
      try {
        onAdded(await addChatMembers(chatId, picked.map(person => person.number)), picked);
      } catch (error) {
        if (isBackendError(error) && error.code === 'CANNOT_ADD_TO_GROUP_CHAT') setRefused((error.params?.numbers as number[] | undefined) ?? []);
        throw error;
      }
    });
  };

  const refusedNames = picked.filter(person => refused.includes(person.number)).map(person => person.name);
  const membersError = refusedNames.length ? t('chats.cantBeAdded', { names: refusedNames.join(', ') }) : feedback.fieldError('memberNumbers');

  return (
    <form className="sm-chat__group-form" onSubmit={add} noValidate>
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      <PeoplePicker picked={picked} refused={refused} members={count} counterLabel={t('chats.placesLeft', { count: GROUP_CHAT_MAX_MEMBERS - count })}
        error={membersError} hidden={inIt} isPicked={isPicked} onToggle={toggle} />

      <div className="sm-dialog__actions">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>{t('common.cancel')}</Button>
        <Button type="submit" size="sm" icon={UserPlus} loading={feedback.busy} disabled={!picked.length}>
          {picked.length ? t('chats.addCount', { count: picked.length }) : t('chats.addMembers')}
        </Button>
      </div>
    </form>
  );
}
