import type { RoomData, RoomRequest } from '../types/rooms';
import { request } from './client';

// every room, inactive ones included, by building, floor and code
export const getRooms = () => request<RoomData[]>('GET', '/rooms/');
export const registerRoom = (room: RoomRequest) => request<RoomData>('POST', '/rooms/', room);
export const editRoom = (id: number, room: RoomRequest) => request<RoomData>('PUT', `/rooms/${id}`, room);
// what already uses it keeps it
export const deactivateRoom = (id: number) => request<RoomData>('PUT', `/rooms/${id}/deactivate`);
// ROOM_OVERLAP if another room took its place meanwhile
export const activateRoom = (id: number) => request<RoomData>('PUT', `/rooms/${id}/activate`);
