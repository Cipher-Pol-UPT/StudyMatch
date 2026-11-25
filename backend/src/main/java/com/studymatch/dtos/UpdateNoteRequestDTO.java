package com.studymatch.dtos;

import com.studymatch.enums.NoteColour;

// only what's sent changes. a move sends x and y together
public record UpdateNoteRequestDTO(String text, NoteColour colour, Double x, Double y) {
}
