import { DoorClosed, Pencil, Plus, Power, PowerOff } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { isBackendError } from '../../api/errors';
import { activateRoom, deactivateRoom, editRoom, getRooms, registerRoom } from '../../api/rooms';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Badge, Button, Card, Dialog, EmptyState, Segmented, Select, Table, useToast, type TableColumn } from '../../studymatch-ui';
import { RoomType } from '../../types/enums';
import type { RoomData, RoomRequest } from '../../types/rooms';
import { RoomForm } from './RoomForm';
import './rooms.css';

type Status = 'all' | 'active' | 'inactive';
const ANY = '';

const byPlace = (a: RoomData, b: RoomData) =>
  a.building.localeCompare(b.building) || a.floor - b.floor || a.code.localeCompare(b.code);
// "Building A" and "building a" are the same building
const sameBuilding = (a: string, b: string) => a.localeCompare(b, undefined, { sensitivity: 'accent' }) === 0;

// the rooms by building, then by floor, in the order the backend sends them
function grouped(rooms: RoomData[]) {
  const buildings: { building: string; floors: { floor: number; rooms: RoomData[] }[] }[] = [];
  for (const room of rooms) {
    let building = buildings.find(item => sameBuilding(item.building, room.building));
    if (!building) buildings.push(building = { building: room.building, floors: [] });
    let floor = building.floors.find(item => item.floor === room.floor);
    if (!floor) building.floors.push(floor = { floor: room.floor, rooms: [] });
    floor.rooms.push(room);
  }
  return buildings;
}

// Staff → Rooms (#333): every room by building and floor, with their type, capacity and place on the floor
export function StaffRoomsPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const loaded = useRemoteData('rooms', getRooms);
  // registered or changed here, on top of what was loaded
  const [changed, setChanged] = useState<RoomData[]>([]);
  // the form: a new room, or the room being edited
  const [editing, setEditing] = useState<{ room?: RoomData }>();
  const [switching, setSwitching] = useState<number>();
  const [building, setBuilding] = useState(ANY);
  const [type, setType] = useState(ANY);
  const [status, setStatus] = useState<Status>('all');
  const rooms = [...new Map([...(loaded.data ?? []), ...changed].map(room => [room.id, room])).values()].sort(byPlace);
  const buildings = grouped(rooms).map(item => item.building);
  const shown = rooms.filter(room => (building === ANY || sameBuilding(room.building, building))
    && (type === ANY || room.type === type)
    && (status === 'all' || room.active === (status === 'active')));

  const retry = () => {
    setChanged([]);
    loaded.retry();
  };

  async function save(request: RoomRequest) {
    const room = editing?.room ? await editRoom(editing.room.id, request) : await registerRoom(request);
    setChanged(list => [...list, room]);
    toast.success(t(editing?.room ? 'campusRooms.saved' : 'campusRooms.registered', { code: room.code }));
    setEditing(undefined);
  }

  // deactivating keeps what already uses the room; reactivating fails if another room took its place meanwhile
  async function toggle(room: RoomData) {
    setSwitching(room.id);
    try {
      const updated = room.active ? await deactivateRoom(room.id) : await activateRoom(room.id);
      setChanged(list => [...list, updated]);
      toast.success(t(updated.active ? 'campusRooms.reactivated' : 'campusRooms.deactivated', { code: updated.code }));
    } catch (error) {
      toast.danger(isBackendError(error) && error.code === 'ROOM_OVERLAP'
        ? t('errors.ROOM_OVERLAP', error.params ?? {}) : t('common.unexpectedError'));
    } finally {
      setSwitching(undefined);
    }
  }

  const columns: TableColumn<RoomData>[] = [
    {
      key: 'code', header: t('campusRooms.code'), width: '22%',
      render: row => (
        <span className="rooms__code" data-active={row.active}>
          {row.code}
          {!row.active && <Badge tone="neutral">{t('campusRooms.inactive')}</Badge>}
        </span>
      ),
    },
    { key: 'type', header: t('campusRooms.type'), width: '16%', render: row => t(`campusRooms.types.${row.type}`) },
    { key: 'capacity', header: t('campusRooms.capacity'), align: 'right', width: '11%', render: row => row.capacity },
    {
      key: 'position', header: t('campusRooms.position'), width: '17%',
      render: row => <span className="rooms__position">{t('campusRooms.place', { x: row.x, y: row.y, width: row.width, depth: row.depth })}</span>,
    },
    {
      key: 'actions', header: <span className="sm-visually-hidden">{t('ui.actions')}</span>, align: 'right', width: '34%',
      render: row => (
        <span className="rooms__actions">
          {row.active && (
            <Button size="sm" variant="ghost" icon={Pencil} aria-label={t('campusRooms.editOf', { code: row.code })}
              onClick={() => setEditing({ room: row })}>
              {t('campusRooms.edit')}
            </Button>
          )}
          <Button size="sm" variant="ghost" icon={row.active ? PowerOff : Power} loading={switching === row.id}
            aria-label={t(row.active ? 'campusRooms.deactivateOf' : 'campusRooms.reactivateOf', { code: row.code })}
            onClick={() => void toggle(row)}>
            {t(row.active ? 'campusRooms.deactivate' : 'campusRooms.reactivate')}
          </Button>
        </span>
      ),
    },
  ];

  return (
    <div className="role-page">
      <header>
        <h1 className="sm-h2">{t('workspace.sections.rooms')}</h1>
        <p className="sm-caption">{t('campusRooms.intro')}</p>
        <Button icon={Plus} className="catalogue-page__create" onClick={() => setEditing({})}>{t('campusRooms.new')}</Button>
      </header>
      {loaded.loading || loaded.error !== undefined ? (
        <DataLoadState error={loaded.error} retry={retry} />
      ) : rooms.length === 0 ? (
        <EmptyState icon={DoorClosed} title={t('campusRooms.none')} body={t('campusRooms.noneBody')}
          action={<Button icon={Plus} onClick={() => setEditing({})}>{t('campusRooms.new')}</Button>} />
      ) : (
        <>
          <div className="rooms__filters">
            <Select label={t('campusRooms.building')} value={building} onChange={event => setBuilding(event.target.value)}
              options={[{ value: ANY, label: t('campusRooms.allBuildings') }, ...buildings.map(name => ({ value: name, label: name }))]} />
            <Select label={t('campusRooms.type')} value={type} onChange={event => setType(event.target.value)}
              options={[{ value: ANY, label: t('campusRooms.allTypes') }, ...RoomType.map(value => ({ value, label: t(`campusRooms.types.${value}`) }))]} />
            <Segmented<Status> label={t('campusRooms.status')} value={status} onValueChange={setStatus} options={[
              { value: 'all', label: t('campusRooms.all') },
              { value: 'active', label: t('campusRooms.active') },
              { value: 'inactive', label: t('campusRooms.inactive') },
            ]} />
          </div>
          {shown.length === 0 ? (
            <EmptyState icon={DoorClosed} compact title={t('campusRooms.noMatch')} body={t('campusRooms.noMatchBody')} />
          ) : grouped(shown).map(group => (
            <Card key={group.building} className="rooms__building">
              <h2 className="sm-h4">{group.building}</h2>
              {group.floors.map(floor => (
                <section key={floor.floor} className="rooms__floor" aria-label={`${group.building}, ${t('campusRooms.floorN', { floor: floor.floor })}`}>
                  <h3 className="rooms__floor-title">{t('campusRooms.floorN', { floor: floor.floor })}</h3>
                  <Table label={`${group.building}, ${t('campusRooms.floorN', { floor: floor.floor })}`} columns={columns}
                    rows={floor.rooms} rowKey={row => row.id} />
                </section>
              ))}
            </Card>
          ))}
        </>
      )}
      <Dialog open={editing !== undefined} onClose={() => setEditing(undefined)} size="lg"
        title={t(editing?.room ? 'campusRooms.editTitle' : 'campusRooms.new')} description={t('campusRooms.formHint')}>
        {editing && (
          <RoomForm key={editing.room?.id ?? 'new'} room={editing.room} buildings={buildings} onSubmit={save}
            onCancel={() => setEditing(undefined)} />
        )}
      </Dialog>
    </div>
  );
}
