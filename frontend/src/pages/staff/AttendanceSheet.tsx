import { Check, Users } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { registerAttendance } from '../../api/attendance';
import { Avatar, Banner, Button, Checkbox, EmptyState, StatusPill, Switch, Table, useFormFeedback, useToast, type TableColumn } from '../../studymatch-ui';
import type { AttendanceRow, AttendanceSheetData } from '../../types/attendance';

export interface AttendanceSheetProps {
  editionId: number;
  subject: string;
  sheet: AttendanceSheetData;
}

// one lesson of one class; a statute student can't be marked, their attendance doesn't count (GR7)
export function AttendanceSheet({ editionId, subject, sheet }: AttendanceSheetProps) {
  const { t, i18n } = useTranslation();
  const toast = useToast();
  const feedback = useFormFeedback();
  const [saved, setSaved] = useState(sheet);
  const [rows, setRows] = useState(sheet.students);
  const absent = rows.filter(row => row.absent).length;
  const lesson = new Intl.DateTimeFormat(i18n.language, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(saved.startsAt));

  // unticking absent also clears justified
  const change = (number: number, value: Partial<AttendanceRow>) =>
    setRows(list => list.map(row => row.number === number ? { ...row, ...value, justified: (value.absent ?? row.absent) && (value.justified ?? row.justified) } : row));

  async function save() {
    const absences = rows.filter(row => row.absent).map(row => ({ studentNumber: row.number, justified: row.justified }));
    const result = await registerAttendance(editionId, { classSection: saved.classSection, startsAt: saved.startsAt, absences });
    setSaved(result);
    setRows(result.students);
    toast.success(t('attendance.saved', { count: absences.length }));
  }

  const columns: TableColumn<AttendanceRow>[] = [
    {
      key: 'name', header: t('attendance.student'),
      render: row => <span className="attendance__student"><Avatar name={row.name} size={32} /><span>{row.name}</span><span className="sm-caption">· {row.number}</span></span>,
    },
    {
      key: 'absent', header: t('attendance.absent'), width: 120,
      render: row => !row.statute && <Checkbox aria-label={t('attendance.absentLabel', { name: row.name })} checked={row.absent} disabled={feedback.busy}
        onCheckedChange={checked => change(row.number, { absent: checked })} />,
    },
    {
      key: 'justified', header: t('attendance.justified'),
      render: row => row.statute
        ? <StatusPill>{t('attendance.statute')}</StatusPill>
        : <Switch aria-label={t('attendance.justifiedLabel', { name: row.name })} checked={row.justified} disabled={!row.absent || feedback.busy}
          onCheckedChange={checked => change(row.number, { justified: checked })} />,
    },
  ];

  return (
    <div className="attendance__sheet">
      {saved.recorded && (
        <Banner title={t('attendance.recordedTitle')}>{t('attendance.recordedBody', { lesson: `${subject} · ${saved.classSection} · ${lesson}` })}</Banner>
      )}
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      <Table label={t('attendance.sheet')} columns={columns} rows={rows} rowKey={row => row.number}
        empty={<EmptyState icon={Users} title={t('attendance.noStudents')} body={t('attendance.noStudentsBody')} />} />
      <div className="attendance__actions">
        <Button icon={Check} loading={feedback.busy} disabled={rows.length === 0} onClick={() => void feedback.submit(save)}>
          {t('attendance.save', { count: absent })}
        </Button>
      </div>
    </div>
  );
}
