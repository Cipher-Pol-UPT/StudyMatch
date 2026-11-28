import type { RoomType } from './enums';

// a room on campus (#332): its place is the top-left cell and size on its floor's grid
export interface RoomData {
  id: number;
  code: string;
  building: string;
  floor: number;
  type: RoomType;
  capacity: number;
  x: number;
  y: number;
  width: number;
  depth: number;
  active: boolean;
}

export type RoomRequest = Omit<RoomData, 'id' | 'active'>;
