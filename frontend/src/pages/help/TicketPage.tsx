import { ArrowLeft, Send } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router-dom';
import { getTicket, replyToTicket } from '../../api/tickets';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Avatar, Banner, Button, Card, StatusPill, Textarea, useFormFeedback } from '../../studymatch-ui';
import type { TicketData } from '../../types/tickets';
import { TEXT_MAX } from './NewTicketForm';
import { formatWhen, STATUS_TONE } from './ticketFormat';
import './help.css';

interface Message {
  key: string;
  mine: boolean;
  name: string;
  // whose initials the avatar shows
  avatar: string;
  role?: string;
  text: string;
  at: string;
}

// one of your tickets: what you wrote, the answers under it and a reply box (#363)
export function TicketPage({ me, myName }: { me: number; myName: string }) {
  const { t } = useTranslation();
  const id = Number(useParams().ticketId);
  const load = useCallback(() => getTicket(id), [id]);
  const loaded = useRemoteData(`ticket-${id}`, load);
  // the thread a reply answered with, newer than what was loaded
  const [replied, setReplied] = useState<TicketData>();
  const ticket = replied ?? loaded.data;

  return (
    <div className="role-page help-ticket">
      <Link to="/help" className="help-ticket__back"><ArrowLeft size={16} aria-hidden /> {t('help.mine')}</Link>
      {loaded.loading || loaded.error !== undefined || !ticket ? (
        <DataLoadState error={loaded.error} retry={loaded.retry} />
      ) : (
        <Thread ticket={ticket} me={me} myName={myName} onReplied={setReplied} />
      )}
    </div>
  );
}

function Thread({ ticket, me, myName, onReplied }: { ticket: TicketData; me: number; myName: string; onReplied: (ticket: TicketData) => void }) {
  const { t, i18n } = useTranslation();
  const feedback = useFormFeedback();
  const [text, setText] = useState('');
  const you = t('help.you');
  // what you wrote when opening it comes first, then the replies in order
  const messages: Message[] = [
    { key: 'description', mine: true, name: you, avatar: myName, text: ticket.description, at: ticket.createdAt },
    ...ticket.replies.map(reply => ({
      key: String(reply.id),
      mine: reply.authorNumber === me,
      name: reply.authorNumber === me ? you : reply.authorName ?? t('help.guest'),
      avatar: reply.authorNumber === me ? myName : reply.authorName ?? t('help.guest'),
      role: reply.authorNumber !== me && reply.authorRole ? t(`help.roles.${reply.authorRole}`) : undefined,
      text: reply.text,
      at: reply.createdAt,
    })),
  ];

  async function send() {
    const updated = await replyToTicket(ticket.id, text.trim());
    onReplied(updated);
    setText('');
  }

  return (
    <>
      <header className="help-ticket__head">
        <div>
          <h1 className="sm-h2">{ticket.subject}</h1>
          <p className="sm-caption">{t('help.openedOn', { category: t(`help.categories.${ticket.category}`), date: formatWhen(ticket.createdAt, i18n.language) })}</p>
        </div>
        <StatusPill tone={STATUS_TONE[ticket.status]}>{t(`help.statuses.${ticket.status}`)}</StatusPill>
      </header>
      <ol className="help-thread" aria-label={t('help.thread')}>
        {messages.map(message => (
          <li key={message.key} className="help-thread__item" data-mine={message.mine}>
            <Avatar name={message.avatar} size={36} />
            <div className="help-thread__body">
              <p className="help-thread__who">
                <b>{message.name}</b>
                {message.role && <span>{message.role}</span>}
                <time dateTime={message.at}>{formatWhen(message.at, i18n.language)}</time>
              </p>
              <p className="help-thread__text">{message.text}</p>
            </div>
          </li>
        ))}
      </ol>
      <Card className="help-reply">
        <form noValidate onSubmit={event => { event.preventDefault(); void feedback.submit(send); }}>
          {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
          {ticket.status === 'RESOLVED' && <Banner tone="info">{t('help.reopens')}</Banner>}
          <Textarea label={t('help.reply')} value={text} maxLength={TEXT_MAX} showCount rows={3} disabled={feedback.busy}
            placeholder={t('help.replyPlaceholder')} error={feedback.fieldError('text')} onChange={event => setText(event.target.value)} />
          <div className="help-form__actions">
            <Button type="submit" icon={Send} loading={feedback.busy} disabled={!text.trim()}>
              {t(ticket.status === 'RESOLVED' ? 'help.replyReopen' : 'help.sendReply')}
            </Button>
          </div>
        </form>
      </Card>
    </>
  );
}
