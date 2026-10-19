import { useTranslation } from 'react-i18next';

export function useStudentFormatting() {
  const { t, i18n } = useTranslation();
  const number = new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 2 });
  return {
    grade: (value: number | null) => value === null ? t('directory.noGrade') : `${number.format(value)} / 20`,
    attendance: (value: number | null) => value === null ? t('directory.noAttendance') : `${number.format(value)}%`,
  };
}