import { useTranslation } from 'react-i18next';

export function useStudentFormatting() {
  const { t, i18n } = useTranslation();
  const number = new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 2 });
  const day = new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium' });
  return {
    grade: (value: number | null) => value === null ? t('directory.noGrade') : `${number.format(value)} / 20`,
    attendance: (value: number | null) => value === null ? t('directory.noAttendance') : `${number.format(value)}%`,
    // the backend sends LocalDate as yyyy-mm-dd, read it as local midnight so it doesn't shift a day
    date: (value: string | null) => value === null ? '—' : day.format(new Date(`${value}T00:00`)),
  };
}
