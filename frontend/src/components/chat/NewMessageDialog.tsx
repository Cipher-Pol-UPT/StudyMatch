import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { searchChatContacts } from '../../api/chats';
import { useRemoteData } from '../../hooks/useRemoteData';
import { ChatPeople, ChatPerson, Dialog, SearchField } from '../../studymatch-ui';
import type { ChatContactData } from '../../types/chat';
import { DataLoadState } from '../feedback/DataLoadState';

export interface NewMessageDialogProps {
  open: boolean;
  onClose: () => void;
  onPick: (contact: ChatContactData) => void;
}

// start a DM: find a Student or Teacher by name or number and open the conversation with them
export function NewMessageDialog({ open, onClose, onPick }: NewMessageDialogProps) {
  const { t } = useTranslation();
  return (
    <Dialog open={open} onClose={onClose} title={t('chats.newMessage')} description={t('chats.newMessageDescription')}>
      {/* only there while open, so every time it starts with an empty search */}
      <ContactSearch onPick={onPick} />
    </Dialog>
  );
}

function ContactSearch({ onPick }: { onPick: (contact: ChatContactData) => void }) {
  const { t } = useTranslation();
  const [search, setSearch] = useState('');
  const query = new URLSearchParams({ ...(search.trim() ? { search: search.trim() } : {}), page: '0' }).toString();
  const load = useCallback(() => searchChatContacts(query), [query]);
  const contacts = useRemoteData(`chat-contacts?${query}`, load);
  const people = contacts.data;

  return (
    <>
      <SearchField label={t('chats.searchPeople')} placeholder={t('chats.searchPlaceholder')} value={search} onValueChange={setSearch} autoFocus />
      {contacts.loading || contacts.error !== undefined || !people ? (
        <DataLoadState error={contacts.error} retry={contacts.retry} />
      ) : people.content.length === 0 ? (
        <p className="sm-caption" role="status">{t('chats.noOneFound')}</p>
      ) : (
        <>
          <ChatPeople label={t('chats.people')}>
            {people.content.map(contact => (
              <ChatPerson key={contact.number} name={contact.name} tag={t(`roles.${contact.role}`)}
                subtitle={t('chats.contactLine', { role: t(`roles.${contact.role}`), number: contact.number })}
                onPick={() => onPick(contact)} />
            ))}
          </ChatPeople>
          {people.totalElements > people.content.length && (
            <p className="sm-caption">{t('chats.narrowDown', { shown: people.content.length, total: people.totalElements })}</p>
          )}
        </>
      )}
    </>
  );
}
