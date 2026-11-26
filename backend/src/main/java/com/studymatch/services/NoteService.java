package com.studymatch.services;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.CreateNoteRequestDTO;
import com.studymatch.dtos.NoteDTO;
import com.studymatch.dtos.UpdateNoteRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Note;
import com.studymatch.models.User;
import com.studymatch.repositories.NoteRepository;

// someone's private sticky notes. every call only finds the owner's own (#303)
@Service
public class NoteService {

  private final NoteRepository noteRepository;

  public NoteService(NoteRepository noteRepository) {
    this.noteRepository = noteRepository;
  }

  @Transactional(readOnly = true)
  public List<NoteDTO> list(Long ownerNumber) {
    return noteRepository.findByOwner_NumberOrderByUpdatedAtAscIdAsc(ownerNumber).stream().map(NoteDTO::from).toList();
  }

  @Transactional
  public NoteDTO create(User owner, CreateNoteRequestDTO request) {
    if (noteRepository.countByOwner_Number(owner.getNumber()) >= Note.MAX_NOTES) {
      throw new StudyMatchException(ErrorCode.NOTE_LIMIT_REACHED, Map.of("max", Note.MAX_NOTES));
    }
    Note note = new Note(owner, request.text(), request.colour(), request.x(), request.y());
    return NoteDTO.from(noteRepository.save(note));
  }

  @Transactional
  public NoteDTO update(Long ownerNumber, Long noteId, UpdateNoteRequestDTO request) {
    Note note = find(ownerNumber, noteId);
    if (request.text() != null) {
      note.setText(request.text());
    }
    if (request.colour() != null) {
      note.setColour(request.colour());
    }
    if (request.x() != null || request.y() != null) {
      note.moveTo(request.x() != null ? request.x() : note.getX(), request.y() != null ? request.y() : note.getY());
    }
    return NoteDTO.from(note);
  }

  @Transactional
  public void delete(Long ownerNumber, Long noteId) {
    noteRepository.delete(find(ownerNumber, noteId));
  }

  private Note find(Long ownerNumber, Long noteId) {
    return noteRepository.findByIdAndOwner_Number(noteId, ownerNumber)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

}
