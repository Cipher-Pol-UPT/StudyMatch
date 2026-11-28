import { DoorOpen } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { isBackendError, type ErrorCode } from '../../api/errors';
import { Banner, Button, NumberStepper, Select, TextField, useFormFeedback } from '../../studymatch-ui';
import { RoomType } from '../../types/enums';
import type { RoomData, RoomRequest } from '../../types/rooms';

// each floor is a GRID × GRID grid (docs/domain.md, Rooms)
export const GRID = 100;
const CODE_MAX = 20;
const BUILDING_MAX = 50;

type Field = 'code' | 'position';

// the backend's rules, each under the field it's about
const FIELD_OF: Partial<Record<ErrorCode, Field>> = {
  ROOM_ALREADY_EXISTS: 'code',
  ROOM_OVERLAP: 'position',
  ROOM_OUTSIDE_FLOOR: 'position',
};

// the numbers stay as typed until sending, so a field can be cleared while typing
type Numbers = 'capacity' | 'x' | 'y' | 'width' | 'depth';
type Draft = Omit<RoomRequest, Numbers> & Record<Numbers, string>;
const NUMBERS: Numbers[] = ['capacity', 'x', 'y', 'width', 'depth'];
// what each number can be, as the backend checks it
const RANGE: Record<Numbers, [number, number]> = { capacity: [1, 1000], x: [0, GRID - 1], y: [0, GRID - 1], width: [1, GRID], depth: [1, GRID] };

const EMPTY: Draft = { code: '', building: '', floor: 0, type: 'CLASSROOM', capacity: '30', x: '0', y: '0', width: '10', depth: '10' };
// only what the request carries, not the room's id or whether it's active
const draftOf = ({ code, building, floor, type, capacity, x, y, width, depth }: RoomData): Draft => ({ code, building, floor, type,
  capacity: String(capacity), x: String(x), y: String(y), width: String(width), depth: String(depth) });
const valid = (draft: Draft, key: Numbers) => {
  const n = Number(draft[key]);
  return draft[key].trim() !== '' && Number.isInteger(n) && n >= RANGE[key][0] && n <= RANGE[key][1];
};

export interface RoomFormProps {
  // the room being edited; a new one without it
  room?: RoomData;
  // the buildings already registered, offered as suggestions
  buildings: string[];
  onSubmit: (room: RoomRequest) => Promise<void>;
  onCancel: () => void;
}

// register or edit a room: what it is, and where it sits on its floor
export function RoomForm({ room, buildings, onSubmit, onCancel }: RoomFormProps) {
  const { t } = useTranslation();
  const feedback = useFormFeedback();
  const [value, setValue] = useState<Draft>(() => room ? draftOf(room) : EMPTY);
  const [codeError, setCodeError] = useState<{ field: Field; message: string } | null>(null);
  const set = <K extends keyof Draft>(key: K) => (next: Draft[K]) => setValue(current => ({ ...current, [key]: next }));
  // checked here first, the backend checks them again
  const wrong = NUMBERS.filter(key => !valid(value, key));
  const outside = !wrong.length && (Number(value.x) + Number(value.width) > GRID || Number(value.y) + Number(value.depth) > GRID);

  const rangeError = (key: Numbers) => wrong.includes(key) && value[key] !== ''
    ? t('campusRooms.between', { min: RANGE[key][0], max: RANGE[key][1] }) : undefined;

  const errorFor = (field: Field | 'building') => {
    if (field === 'position' && outside) return t('errors.ROOM_OUTSIDE_FLOOR', { size: GRID });
    if (codeError?.field === field) return codeError.message;
    if (field === 'position') return ['x', 'y', 'width', 'depth'].map(feedback.fieldError).find(Boolean);
    return feedback.fieldError(field);
  };

  async function save() {
    setCodeError(null);
    if (wrong.length || outside) return;
    try {
      await onSubmit({ ...value, code: value.code.trim(), building: value.building.trim(), capacity: Number(value.capacity),
        x: Number(value.x), y: Number(value.y), width: Number(value.width), depth: Number(value.depth) });
    } catch (error) {
      const field = isBackendError(error) ? FIELD_OF[error.code] : undefined;
      if (!field || !isBackendError(error)) throw error;
      setCodeError({ field, message: t(`errors.${error.code}`, error.params ?? {}) });
    }
  }

  const position = errorFor('position');
  return (
    <form className="rooms-form" noValidate onSubmit={event => { event.preventDefault(); void feedback.submit(save); }}>
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      <div className="rooms-form__row">
        <TextField label={t('campusRooms.code')} value={value.code} maxLength={CODE_MAX} placeholder="B.101" required disabled={feedback.busy}
          error={errorFor('code')} onChange={event => set('code')(event.target.value)} />
        <Select label={t('campusRooms.type')} value={value.type} disabled={feedback.busy}
          options={RoomType.map(type => ({ value: type, label: t(`campusRooms.types.${type}`) }))}
          onChange={event => set('type')(event.target.value as RoomType)} />
      </div>
      <div className="rooms-form__row">
        <TextField label={t('campusRooms.building')} value={value.building} maxLength={BUILDING_MAX} list="rooms-buildings" required
          disabled={feedback.busy} error={errorFor('building')} onChange={event => set('building')(event.target.value)} />
        <datalist id="rooms-buildings">{buildings.map(building => <option key={building} value={building} />)}</datalist>
        <div className="rooms-form__box">
          <NumberStepper label={t('campusRooms.floor')} value={value.floor} min={-5} max={50} disabled={feedback.busy}
            hint={t('campusRooms.floorHint')} error={feedback.fieldError('floor')} onValueChange={set('floor')} />
        </div>
      </div>
      <TextField label={t('campusRooms.capacity')} type="number" inputMode="numeric" min={1} max={1000} value={value.capacity}
        hint={t('campusRooms.capacityHint')} disabled={feedback.busy} error={rangeError('capacity') ?? feedback.fieldError('capacity')}
        onChange={event => set('capacity')(event.target.value)} />
      <fieldset className="rooms-form__position" aria-describedby="rooms-position-hint">
        <legend className="rooms-form__legend">{t('campusRooms.position')}</legend>
        <p id="rooms-position-hint" className="sm-caption">{t('campusRooms.positionHint', { size: GRID })}</p>
        <div className="rooms-form__grid">
          {(['x', 'y', 'width', 'depth'] as const).map(key => (
            <TextField key={key} label={t(`campusRooms.${key}`)} type="number" inputMode="numeric" min={RANGE[key][0]} max={RANGE[key][1]}
              value={value[key]} disabled={feedback.busy} error={rangeError(key)} onChange={event => set(key)(event.target.value)} />
          ))}
        </div>
        {position && <p className="rooms-form__error" role="alert">{position}</p>}
      </fieldset>
      <div className="rooms-form__actions">
        <Button variant="ghost" onClick={onCancel} disabled={feedback.busy}>{t('common.cancel')}</Button>
        <Button type="submit" icon={DoorOpen} loading={feedback.busy} disabled={!value.code.trim() || !value.building.trim() || wrong.length > 0 || outside}>
          {room ? t('common.save') : t('campusRooms.register')}
        </Button>
      </div>
    </form>
  );
}
