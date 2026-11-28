package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.CreateNoteRequestDTO;
import com.studymatch.dtos.NoteDTO;
import com.studymatch.dtos.UpdateNoteRequestDTO;
import com.studymatch.enums.NoteColour;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Note;
import com.studymatch.models.Student;
import com.studymatch.repositories.NoteRepository;

class NoteServiceTest {

  private static final Long OWNER = 1001L;

  private NoteRepository noteRepository;
  private NoteService noteService;
  private Student owner;

  @BeforeEach
  void setUp() {
    noteRepository = mock(NoteRepository.class);
    noteService = new NoteService(noteRepository);
    owner = ModelTestData.student("Ana");
    ReflectionTestUtils.setField(owner, "number", OWNER);
    when(noteRepository.save(any(Note.class))).thenAnswer(call -> call.getArgument(0));
  }

  private Note note(String text) {
    return new Note(owner, text, NoteColour.YELLOW, 10, 20);
  }

  @Test
  @DisplayName("Listing gives the owner's notes in the repository's order")
  void list() {
    // Arrange
    when(noteRepository.findByOwner_NumberOrderByUpdatedAtAscIdAsc(OWNER)).thenReturn(List.of(note("a"), note("b")));

    // Act
    List<NoteDTO> notes = noteService.list(OWNER);

    // Assert
    assertEquals(List.of("a", "b"), notes.stream().map(NoteDTO::text).toList());
  }

  @Test
  @DisplayName("The 20th note can still be added")
  void nineteenNotes() {
    // Arrange
    when(noteRepository.countByOwner_Number(OWNER)).thenReturn(19L);

    // Act
    NoteDTO created = noteService.create(owner, new CreateNoteRequestDTO("hi", NoteColour.BLUE, 30.0, 40.0));

    // Assert
    assertEquals("hi", created.text());
    assertEquals(NoteColour.BLUE, created.colour());
    verify(noteRepository).save(any(Note.class));
  }

  @Test
  @DisplayName("With 20 notes a new one is NOTE_LIMIT_REACHED and nothing is saved")
  void twentyNotes() {
    // Arrange
    when(noteRepository.countByOwner_Number(OWNER)).thenReturn(20L);
    CreateNoteRequestDTO request = new CreateNoteRequestDTO("hi", NoteColour.BLUE, 30.0, 40.0);

    // Act
    StudyMatchException exception = assertThrows(StudyMatchException.class, () -> noteService.create(owner, request));

    // Assert
    assertEquals(ErrorCode.NOTE_LIMIT_REACHED, exception.getCode());
    verify(noteRepository, never()).save(any(Note.class));
  }

  @Test
  @DisplayName("Over 20 notes is refused too")
  void overTwentyNotes() {
    // Arrange
    when(noteRepository.countByOwner_Number(OWNER)).thenReturn(21L);
    CreateNoteRequestDTO request = new CreateNoteRequestDTO("hi", NoteColour.BLUE, 30.0, 40.0);

    // Act + Assert
    assertEquals(ErrorCode.NOTE_LIMIT_REACHED,
        assertThrows(StudyMatchException.class, () -> noteService.create(owner, request)).getCode());
  }

  @Test
  @DisplayName("A text over 500 characters isn't saved")
  void createTooLong() {
    // Arrange
    CreateNoteRequestDTO request = new CreateNoteRequestDTO("a".repeat(501), NoteColour.BLUE, 30.0, 40.0);

    // Act + Assert
    assertEquals(ErrorCode.NOTE_TOO_LONG,
        assertThrows(StudyMatchException.class, () -> noteService.create(owner, request)).getCode());
    verify(noteRepository, never()).save(any(Note.class));
  }

  @Test
  @DisplayName("Someone else's note, or one that doesn't exist, is NOT_FOUND")
  void someoneElsesNote() {
    // Arrange
    when(noteRepository.findByIdAndOwner_Number(5L, OWNER)).thenReturn(Optional.empty());
    UpdateNoteRequestDTO request = new UpdateNoteRequestDTO("mine now", null, null, null);

    // Act + Assert
    assertEquals(ErrorCode.NOT_FOUND,
        assertThrows(StudyMatchException.class, () -> noteService.update(OWNER, 5L, request)).getCode());
    assertEquals(ErrorCode.NOT_FOUND,
        assertThrows(StudyMatchException.class, () -> noteService.delete(OWNER, 5L)).getCode());
    verify(noteRepository, never()).delete(any(Note.class));
  }

  @Test
  @DisplayName("Changing only the colour keeps the text and position")
  void updateOnlyColour() {
    // Arrange
    when(noteRepository.findByIdAndOwner_Number(5L, OWNER)).thenReturn(Optional.of(note("keep me")));

    // Act
    NoteDTO updated = noteService.update(OWNER, 5L, new UpdateNoteRequestDTO(null, NoteColour.GREEN, null, null));

    // Assert
    assertEquals("keep me", updated.text());
    assertEquals(NoteColour.GREEN, updated.colour());
    assertEquals(10, updated.x());
    assertEquals(20, updated.y());
  }

  @Test
  @DisplayName("Moving sends x and y, one of them alone keeps the other")
  void updatePosition() {
    // Arrange
    when(noteRepository.findByIdAndOwner_Number(5L, OWNER)).thenReturn(Optional.of(note("move me")));

    // Act
    NoteDTO moved = noteService.update(OWNER, 5L, new UpdateNoteRequestDTO(null, null, 70.5, null));

    // Assert
    assertEquals(70.5, moved.x());
    assertEquals(20, moved.y());
    assertEquals("move me", moved.text());
  }

  @Test
  @DisplayName("Deleting finds the note among the owner's and removes it")
  void delete() {
    // Arrange
    Note note = note("bye");
    when(noteRepository.findByIdAndOwner_Number(5L, OWNER)).thenReturn(Optional.of(note));

    // Act
    noteService.delete(OWNER, 5L);

    // Assert
    verify(noteRepository).delete(note);
  }
}
