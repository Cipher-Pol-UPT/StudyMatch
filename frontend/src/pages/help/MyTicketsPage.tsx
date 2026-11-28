import { LifeBuoy, Plus } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { getMyTickets, openTicket } from '../../api/tickets';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Button, Dialog, EmptyState, StatusPill, Table, useToast, type TableColumn } from '../../studymatch-ui';
import type { OpenTicketRequest, TicketSummaryData } from '../../types/tickets';
import { NewTicketForm } from './NewTicketForm';
import { formatWhen, STATUS_TONE } from './ticketFormat';
import './help.css';

// Help (#363): your tickets, the last one replied to first, and New ticket
export function MyTicketsPage() {
  const { t, i18n } = useTranslation();
  const toast = useToast();
  const navigate = useNavigate();
  const loaded = useRemoteData('my-tickets', getMyTickets);
  // opened here, on top of what was loaded
  const [opened, setOpened] = useState<TicketSummaryData[]>([]);
  const [writing, setWriting] = useState(false);
  const tickets = [...opened, ...(loaded.data ?? []).filter(ticket => !opened.some(item => item.id === ticket.id))];

  const retry = () => {
    setOpened([]);
    loaded.retry();
  };

  async function open(request: OpenTicketRequest) {
    const ticket = await openTicket(request);
    setOpened(list => [ticket, ...list]);
    setWriting(false);
    toast.success(t('help.opened'));
  }

  const columns: TableColumn<TicketSummaryData>[] = [
    { key: 'subject', header: t('help.subject'), render: row => <span className="help__subject">{row.subject}</span> },
    { key: 'category', header: t('help.category'), render: row => t(`help.categories.${row.category}`) },
    { key: 'status', header: t('help.status'), render: row => <StatusPill tone={STATUS_TONE[row.status]} size="sm">{t(`help.statuses.${row.status}`)}</StatusPill> },
    { key: 'updated', header: t('help.updated'), align: 'right', render: row => formatWhen(row.updatedAt, i18n.language) },
  ];

  return (
    <div className="role-page help">
      <header>
        <h1 className="sm-h2">{t('help.title')}</h1>
        <p className="sm-caption">{t('help.intro')}</p>
        <Button icon={Plus} className="catalogue-page__create" onClick={() => setWriting(true)}>{t('help.new')}</Button>
      </header>
      {loaded.loading || loaded.error !== undefined ? (
        <DataLoadState error={loaded.error} retry={retry} />
      ) : (
        <Table label={t('help.mine')} columns={columns} rows={tickets} rowKey={row => row.id}
          onRowClick={row => navigate(`/help/${row.id}`)}
          empty={<EmptyState icon={LifeBuoy} title={t('help.none')} body={t('help.noneBody')}
            action={<Button icon={Plus} onClick={() => setWriting(true)}>{t('help.new')}</Button>} />} />
      )}
      <Dialog open={writing} onClose={() => setWriting(false)} size="lg" title={t('help.new')} description={t('help.newHint')}>
        {writing && <NewTicketForm onSubmit={open} onCancel={() => setWriting(false)} />}
      </Dialog>
    </div>
  );
}
