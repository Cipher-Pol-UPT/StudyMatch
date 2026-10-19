import { LogOut } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { logout } from '../../api/auth';
import { Avatar, Menu, type MenuEntry } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';

export interface AccountMenuProps {
  user: CurrentUser;
  avatarUrl?: string;
  items?: MenuEntry[];
  onLoggedOut: () => void;
}

export function AccountMenu({ user, avatarUrl, items = [], onLoggedOut }: AccountMenuProps) {
  const { t } = useTranslation();

  const signOut = () => {
    logout().catch(() => undefined).finally(onLoggedOut);
  };

  const entries: MenuEntry[] = [
    { type: 'label', label: user.name },
    ...items,
    ...(items.length ? [{ type: 'separator' } as MenuEntry] : []),
    { label: t('nav.logout'), icon: LogOut, danger: true, onSelect: signOut },
  ];

  return (
    <Menu
      label={t('nav.accountMenu')}
      items={entries}
      trigger={
        <button type="button" className="profile-menu__trigger" aria-label={t('nav.accountMenu')}>
          <Avatar name={user.name} src={avatarUrl} size={36} />
        </button>
      }
    />
  );
}
