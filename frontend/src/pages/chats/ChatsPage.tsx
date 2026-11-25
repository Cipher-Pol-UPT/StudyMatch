import { ArrowLeft, MessagesSquare, SquarePen } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getDirectChat } from '../../api/chats';
import { isBackendError } from '../../api/errors';
import { NewMessageDialog } from '../../components/chat/NewMessageDialog';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Banner, Button, buttonClass, ChatLayout, ChatListPane, ChatThread, EmptyState } from '../../studymatch-ui';
import { type ChatContactData } from '../../types/chat';
import { Conversation } from './Conversation';

export interface ChatsPageProps {
  // where someone's profile is, for the people who have one you can open
  profilePath?: (contact: ChatContactData) => string | undefined;
}

// Chats: the list (#201) and the open conversation; this page starts DMs (#199)
export function ChatsPage({ profilePath }: ChatsPageProps = {}) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { number } = useParams();
  const [choosing, setChoosing] = useState(false);
  const newMessage = (
    <Button size="sm" icon={SquarePen} onClick={() => setChoosing(true)}>{t('chats.newMessage')}</Button>
  );

  return (
    <>
      <ChatLayout label={t('chats.title')} view={number ? 'thread' : 'list'}
        list={(
          <ChatListPane title={t('chats.title')} actions={newMessage}>
            {/* the DMs only show here after their first message, which the list brings in #201 */}
            <EmptyState compact icon={MessagesSquare} title={t('chats.noChats')} body={t('chats.noChatsBody')} />
          </ChatListPane>
        )}
        thread={number ? <DirectConversation key={number} number={Number(number)} profilePath={profilePath} /> : (
          <ChatThread label={t('chats.conversation')}>
            <EmptyState compact icon={MessagesSquare} title={t('chats.pickChat')} body={t('chats.pickChatBody')} />
          </ChatThread>
        )} />
      <NewMessageDialog open={choosing} onClose={() => setChoosing(false)}
        onPick={contact => { setChoosing(false); navigate(`/chats/direct/${contact.number}`); }} />
    </>
  );
}

// the DM with one person: found by their number, then its messages and the message box
function DirectConversation({ number, profilePath }: { number: number } & ChatsPageProps) {
  const { t } = useTranslation();
  const load = useCallback(() => Number.isInteger(number) ? getDirectChat(number) : Promise.reject({ code: 'NOT_FOUND' }), [number]);
  const chat = useRemoteData(`direct-chat:${number}`, load);

  if (chat.error !== undefined) {
    return (
      <ChatThread label={t('chats.conversation')}>
        <Banner tone="danger">{isBackendError(chat.error) ? t(`errors.${chat.error.code}`, chat.error.params ?? {}) : t('common.unexpectedError')}</Banner>
        <Link to="/chats" className={buttonClass({ variant: 'secondary', size: 'sm' })}><ArrowLeft size={16} aria-hidden />{t('chats.back')}</Link>
      </ChatThread>
    );
  }
  if (chat.loading || !chat.data) {
    return <ChatThread label={t('chats.conversation')}><DataLoadState /></ChatThread>;
  }

  return <Conversation chat={chat.data} profilePath={profilePath} />;
}
