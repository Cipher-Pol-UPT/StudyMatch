import { Sparkles } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Avatar, Banner, Card, StatusPill } from '../../studymatch-ui';
import type { GroupMemberData, GroupStatus } from '../../types/groups';
import './groupCard.css';

interface GroupCardProps {
  number: number;
  members: GroupMemberData[];
  status?: GroupStatus;
  // why it was formed, under the members
  explanation?: ReactNode;
  // something the teacher should look at, above the members
  warning?: ReactNode;
}

// one group: shared by generate, view, edit and import groups
export function GroupCard({ number, members, status, explanation, warning }: GroupCardProps) {
  const { t } = useTranslation();
  return (
    <Card title={t('groups.groupNumber', { number })} description={t('groups.size', { count: members.length })}
      action={status && <StatusPill size="sm" tone={status === 'DRAFT' ? 'warning' : 'success'}>{t(`groupStatuses.${status}`)}</StatusPill>}>
      <div className="group-card">
        {warning && <Banner tone="warning">{warning}</Banner>}
        <ul className="group-card__members">
          {members.map(member => (
            <li key={member.number}>
              <Avatar name={member.name} size={28} />
              <span>{member.name} <span className="sm-caption">· {member.classSection}</span></span>
            </li>
          ))}
        </ul>
        {explanation && <p className="sm-caption group-card__why"><Sparkles size={14} aria-hidden="true" />{explanation}</p>}
      </div>
    </Card>
  );
}
