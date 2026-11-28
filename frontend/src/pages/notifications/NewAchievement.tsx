import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ACHIEVEMENT_ART, AchievementDialog, useToast, type AchievementId } from '../../studymatch-ui';
import { useNotifications } from './notificationCenter';

// the achievements already shown here, so each one pops up once, the next time the student opens the app
const SHOWN_KEY = 'studymatch.achievementsShown';

function shown(): number[] {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(SHOWN_KEY) ?? '[]');
    return Array.isArray(value) ? value.filter((id): id is number => typeof id === 'number') : [];
  } catch {
    return [];
  }
}

function remember(id: number) {
  try {
    localStorage.setItem(SHOWN_KEY, JSON.stringify([...shown(), id].slice(-50)));
  } catch {
    // without storage it can show again next time, which is harmless
  }
}

// a new achievement shows its badge in a dialog and a toast; it stays in the list like any notification (#145)
export function NewAchievement() {
  const { t } = useTranslation();
  const toast = useToast();
  const { items } = useNotifications();
  const [seen, setSeen] = useState(shown);
  const toasted = useRef<number>(undefined);
  const next = items?.find(item => item.type === 'ACHIEVEMENT_EARNED' && !item.read && !seen.includes(item.id)
    && typeof item.params.achievement === 'string' && item.params.achievement in ACHIEVEMENT_ART);
  const achievement = next?.params.achievement as AchievementId | undefined;

  useEffect(() => {
    if (!next || !achievement || toasted.current === next.id) return;
    toasted.current = next.id;
    toast.success(t('notifications.newAchievement', { name: t(`achievements.${achievement}.name`) }));
  }, [next, achievement, t, toast]);

  const close = () => {
    if (!next) return;
    remember(next.id);
    setSeen(list => [...list, next.id]);
  };

  // earned, so the badge shows unlocked with its date
  return <AchievementDialog id={achievement ?? null} earnedAt={next?.createdAt} onClose={close} />;
}
