import type { NoteColour } from './enums';

// a sticky note on the home page (#303): x and y are percentages of the board
export interface NoteData {
  id: number; text: string; colour: NoteColour; x: number; y: number; createdAt: string; updatedAt: string;
}
export interface CreateNoteRequest { text: string; colour: NoteColour; x: number; y: number }
// only what's sent changes
export type UpdateNoteRequest = Partial<CreateNoteRequest>;

export const MAX_NOTES = 20;
