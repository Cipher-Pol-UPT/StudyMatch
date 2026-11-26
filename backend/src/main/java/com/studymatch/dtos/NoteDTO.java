package com.studymatch.dtos;

import java.time.LocalDateTime;

import com.studymatch.enums.NoteColour;
import com.studymatch.models.Note;

public record NoteDTO(Long id, String text, NoteColour colour, double x, double y, LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static NoteDTO from(Note note) {
    return new NoteDTO(note.getId(), note.getText(), note.getColour(), note.getX(), note.getY(), note.getCreatedAt(),
        note.getUpdatedAt());
  }

}
