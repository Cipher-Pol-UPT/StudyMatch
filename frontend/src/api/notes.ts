import type { CreateNoteRequest, NoteData, UpdateNoteRequest } from '../types/notes';
import { request } from './client';

// oldest change first, so the last one touched is drawn on top
export const getNotes = () => request<NoteData[]>('GET', '/notes/');

export const createNote = (note: CreateNoteRequest) => request<NoteData>('POST', '/notes/', note);

export const updateNote = (id: number, changes: UpdateNoteRequest) => request<NoteData>('PATCH', `/notes/${id}`, changes);

export const deleteNote = (id: number) => request<void>('DELETE', `/notes/${id}`);
