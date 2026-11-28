import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { activateRoom, deactivateRoom, editRoom, getRooms, registerRoom } from '../src/api/rooms';
import i18n from '../src/i18n';
import { StaffRoomsPage } from '../src/pages/staff/StaffRoomsPage';
import { StudyMatchProvider } from '../src/studymatch-ui';
import type { RoomData } from '../src/types/rooms';

vi.mock('../src/api/rooms', () => ({
  getRooms: vi.fn(),
  registerRoom: vi.fn(),
  editRoom: vi.fn(),
  deactivateRoom: vi.fn(),
  activateRoom: vi.fn(),
}));

const lab: RoomData = { id: 1, code: 'A.101', building: 'Building A', floor: 1, type: 'LAB', capacity: 24, x: 0, y: 0, width: 20, depth: 15, active: true };
const classroom: RoomData = { id: 2, code: 'A.201', building: 'Building A', floor: 2, type: 'CLASSROOM', capacity: 40, x: 10, y: 10, width: 30, depth: 20, active: true };
const old: RoomData = { id: 3, code: 'B.001', building: 'Building B', floor: 0, type: 'AUDITORIUM', capacity: 200, x: 0, y: 0, width: 60, depth: 40, active: false };

const dialog = () => screen.getByRole('dialog');
const field = (label: string) => within(dialog()).getByLabelText(label) as HTMLInputElement;
const type = (label: string, value: string) => fireEvent.change(field(label), { target: { value } });
// the first cell of every room's row, across the tables
const codes = () => [...document.querySelectorAll('tbody tr')].map(row => row.querySelector('td')?.textContent);

function renderPage() {
  render(<StudyMatchProvider><StaffRoomsPage /></StudyMatchProvider>);
}

async function fillNew() {
  fireEvent.click(await screen.findByRole('button', { name: 'New room' }));
  type('Code', ' B.102 ');
  type('Building', 'Building B');
  type('Capacity', '30');
  type('X', '40');
  type('Y', '5');
  type('Width', '12');
  type('Depth', '8');
}

beforeEach(async () => {
  await i18n.changeLanguage('en');
  vi.mocked(getRooms).mockResolvedValue([lab, classroom, old]);
});
afterEach(() => {
  cleanup();
  vi.mocked(registerRoom).mockReset();
  vi.mocked(editRoom).mockReset();
  vi.mocked(deactivateRoom).mockReset();
  vi.mocked(activateRoom).mockReset();
});

describe('manage rooms (staff)', () => {
  it('lists the rooms by building and floor, the inactive one with Reactivate', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Building A' })).toBeTruthy();
    expect(screen.getByRole('table', { name: 'Building A, Floor 1' })).toBeTruthy();
    expect(screen.getByRole('table', { name: 'Building A, Floor 2' })).toBeTruthy();
    const basement = screen.getByRole('table', { name: 'Building B, Floor 0' });
    expect(within(basement).getByText('Inactive')).toBeTruthy();
    expect(within(basement).getByRole('button', { name: 'Reactivate B.001' })).toBeTruthy();
    expect(within(basement).queryByRole('button', { name: 'Edit B.001' })).toBeNull();
    expect(within(screen.getByRole('table', { name: 'Building A, Floor 1' })).getByText('0, 0 · 20 × 15')).toBeTruthy();
  });

  it('filters by building, type and status', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'Building A' });

    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'LAB' } });
    expect(codes()).toEqual(['A.101']);
    fireEvent.change(screen.getByLabelText('Type'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('radio', { name: 'Inactive' }));
    expect(codes()).toEqual(['B.001Inactive']);
    fireEvent.click(screen.getByRole('radio', { name: 'All' }));
    fireEvent.change(screen.getByLabelText('Building'), { target: { value: 'Building A' } });
    expect(codes()).toEqual(['A.101', 'A.201']);
  });

  it('registers a room with the code trimmed and shows it under its building and floor', async () => {
    vi.mocked(registerRoom).mockResolvedValue({ id: 4, code: 'B.102', building: 'Building B', floor: 0, type: 'CLASSROOM', capacity: 30, x: 40, y: 5, width: 12, depth: 8, active: true });
    renderPage();
    await fillNew();
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Register room' }));

    await waitFor(() => expect(registerRoom).toHaveBeenCalledWith({ code: 'B.102', building: 'Building B', floor: 0, type: 'CLASSROOM', capacity: 30, x: 40, y: 5, width: 12, depth: 8 }));
    expect(await screen.findByText('Room B.102 registered.')).toBeTruthy();
    expect(within(screen.getByRole('table', { name: 'Building B, Floor 0' })).getByText('B.102')).toBeTruthy();
  });

  it('a code that is taken shows under the code, and an overlap under the position', async () => {
    vi.mocked(registerRoom).mockRejectedValueOnce({ code: 'ROOM_ALREADY_EXISTS' })
      .mockRejectedValueOnce({ code: 'ROOM_OVERLAP', params: { code: 'A.101' } });
    renderPage();
    await fillNew();
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Register room' }));
    expect(await within(dialog()).findByText('A room with this code already exists.')).toBeTruthy();
    expect(field('Code').getAttribute('aria-invalid')).toBe('true');

    fireEvent.click(within(dialog()).getByRole('button', { name: 'Register room' }));
    expect(await within(within(dialog()).getByRole('group', { name: 'Position' })).findByText('This position overlaps room A.101 on the same floor.')).toBeTruthy();
    expect(within(dialog()).queryByText('A room with this code already exists.')).toBeNull();
  });

  it("doesn't send a room that goes past the floor's edge or a number out of range", async () => {
    renderPage();
    await fillNew();
    type('X', '95');
    expect(within(dialog()).getByText("The room has to fit inside the floor's 100 × 100 grid.")).toBeTruthy();
    expect(within(dialog()).getByRole('button', { name: 'Register room' }).hasAttribute('disabled')).toBe(true);
    type('X', '40');
    type('Capacity', '0');
    expect(within(dialog()).getByText('From 1 to 1000.')).toBeTruthy();
    expect(within(dialog()).getByRole('button', { name: 'Register room' }).hasAttribute('disabled')).toBe(true);
    expect(registerRoom).not.toHaveBeenCalled();
  });

  it('edits a room from its current values', async () => {
    vi.mocked(editRoom).mockResolvedValue({ ...classroom, capacity: 45 });
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Edit A.201' }));
    expect(field('Code').value).toBe('A.201');
    expect(field('Width').value).toBe('30');
    type('Capacity', '45');
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(editRoom).toHaveBeenCalledWith(2, { code: 'A.201', building: 'Building A', floor: 2, type: 'CLASSROOM', capacity: 45, x: 10, y: 10, width: 30, depth: 20 }));
    expect(await screen.findByText('Room A.201 saved.')).toBeTruthy();
    expect(within(screen.getByRole('table', { name: 'Building A, Floor 2' })).getByText('45')).toBeTruthy();
  });

  it('deactivates a room, which then offers Reactivate', async () => {
    vi.mocked(deactivateRoom).mockResolvedValue({ ...lab, active: false });
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Deactivate A.101' }));

    expect(await screen.findByRole('button', { name: 'Reactivate A.101' })).toBeTruthy();
    expect(deactivateRoom).toHaveBeenCalledWith(1);
    expect(screen.getByText('Room A.101 deactivated. What already uses it keeps it.')).toBeTruthy();
  });

  it("says which room took an inactive room's place when it can't be reactivated", async () => {
    vi.mocked(activateRoom).mockRejectedValue({ code: 'ROOM_OVERLAP', params: { code: 'B.002' } });
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Reactivate B.001' }));

    expect(await screen.findByText('This position overlaps room B.002 on the same floor.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Reactivate B.001' })).toBeTruthy();
  });

  it('with no rooms it says so and offers New room', async () => {
    vi.mocked(getRooms).mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText('No rooms yet')).toBeTruthy();
    expect(screen.getAllByRole('button', { name: 'New room' })).toHaveLength(2);
  });
});
