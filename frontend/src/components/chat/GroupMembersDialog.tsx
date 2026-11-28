import { UserMinus } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getChatMembers, removeChatMember } from '../../api/chats';
import { isBackendError } from '../../api/errors';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Button, ChatPersonRow, ConfirmDialog, Dialog, useToast } from '../../studymatch-ui';
import type { ChatContactData } from '../../types/chat';
import { DataLoadState } from '../feedback/DataLoadState';

export interface GroupMembersDialogProps {
  open: boolean;
  chatId: number;
  name: string;
  // your number: the owner is the first member, and only they see Remove
  me: number;
  // an assignment group's members follow its group, nobody removes them by hand (#236)
  fixed?: boolean;
  onClose: () => void;
  // the members after someone was removed
  onChanged: (members: ChatContactData[]) => void;
}

// only students have a photo
const photoOf = (contact: ChatContactData) => contact.role === 'STUDENT' ? `/api/students/${contact.number}/pfp` : undefined;

// who's in a group chat, the owner first, with Remove for the owner (#231)
export function GroupMembersDialog({ open, chatId, name, me, fixed, onClose, onChanged }: GroupMembersDialogProps) {
  const { t } = useTranslation();
  return (
    <Dialog open={open} onClose={onClose} title={t('chats.membersTitle', { name })}>
      {/* only there while open: it asks who's in it each time, nothing is pushed when someone else changes them */}
      {open && <MemberList chatId={chatId} name={name} me={me} fixed={fixed} onChanged={onChanged} />}
    </Dialog>
  );
}

function MemberList({ chatId, name, me, fixed, onChanged }: Omit<GroupMembersDialogProps, 'open' | 'onClose'>) {
  const { t } = useTranslation();
  const toast = useToast();
  const load = useCallback(() => getChatMembers(chatId), [chatId]);
  const members = useRemoteData(`chat-members-list:${chatId}`, load);
  // the members after removing someone here
  const [latest, setLatest] = useState<ChatContactData[]>();
  const [removing, setRemoving] = useState<ChatContactData>();

  if (members.loading || members.error !== undefined || !members.data) return <DataLoadState error={members.error} retry={members.retry} />;
  const list = latest ?? members.data;
  const owner = list[0]?.number;
  const canRemove = owner === me && !fixed;

  const remove = async (person: ChatContactData) => {
    try {
      const now = await removeChatMember(chatId, person.number);
      setLatest(now);
      onChanged(now);
      toast.success(t('chats.memberRemoved', { name: person.name, chat: name }));
    } catch (error) {
      toast.danger(isBackendError(error) ? t(`errors.${error.code}`, error.params ?? {}) : t('common.unexpectedError'));
    }
  };

  return (
    <>
      <p className="sm-chat__members-count">{t('chats.memberCount', { count: list.length })}</p>
      <ul className="sm-chat__people sm-chat__members" aria-label={t('chats.membersTitle', { name })}>
        {list.map(person => (
          <ChatPersonRow key={person.number} name={person.name} avatarSrc={photoOf(person)}
            subtitle={t('chats.contactLine', { role: t(`roles.${person.role}`), number: person.number })}
            action={(
              <span className="sm-chat__member-end">
                {person.number === owner && <span className="sm-person__tag">{t('chats.owner')}</span>}
                {person.number === me && <span className="sm-person__tag">{t('chats.you')}</span>}
                {canRemove && person.number !== me && (
                  <Button size="sm" variant="secondary" onClick={() => setRemoving(person)} aria-label={t('chats.removePerson', { name: person.name })}>
                    {t('chats.remove')}
                  </Button>
                )}
              </span>
            )} />
        ))}
      </ul>
      {removing && (
        <ConfirmDialog open onClose={() => setRemoving(undefined)} tone="danger" icon={UserMinus}
          title={t('chats.removeTitle', { name: removing.name })} description={t('chats.removeBody', { chat: name })} confirmLabel={t('chats.remove')}
          onConfirm={async () => { const person = removing; setRemoving(undefined); await remove(person); }} />
      )}
    </>
  );
}
