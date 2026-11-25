package com.studymatch.dtos;

import com.studymatch.enums.NoteColour;

import jakarta.validation.constraints.NotNull;

// the text can be empty, the note checks its length and position
public record CreateNoteRequestDTO(String text, @NotNull NoteColour colour, @NotNull Double x, @NotNull Double y) {
}
