package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.enums.NoteColour;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class NoteTest {

  private Student owner;

  @BeforeEach
  void setUp() {
    owner = ModelTestData.student("Ana");
  }

  @Test
  @DisplayName("A new note has its owner, text, colour, position and dates")
  void newNote() {
    // Act
    Note note = new Note(owner, "buy pens", NoteColour.PINK, 10, 20);

    // Assert
    assertSame(owner, note.getOwner());
    assertEquals("buy pens", note.getText());
    assertEquals(NoteColour.PINK, note.getColour());
    assertEquals(10, note.getX());
    assertEquals(20, note.getY());
    assertNotNull(note.getCreatedAt());
    assertEquals(note.getCreatedAt(), note.getUpdatedAt());
  }

  @Test
  @DisplayName("The spaces around the text are left out")
  void textIsStripped() {
    // Act
    Note note = new Note(owner, "  buy pens \n", NoteColour.YELLOW, 0, 0);

    // Assert
    assertEquals("buy pens", note.getText());
  }

  @Test
  @DisplayName("A note can be empty, and no text counts as empty")
  void emptyText() {
    // Act
    Note blank = new Note(owner, "   ", NoteColour.YELLOW, 0, 0);
    Note missing = new Note(owner, null, NoteColour.YELLOW, 0, 0);

    // Assert
    assertEquals("", blank.getText());
    assertEquals("", missing.getText());
  }

  @Test
  @DisplayName("499 and 500 characters are fine")
  void textUpToTheLimit() {
    // Act + Assert
    assertDoesNotThrow(() -> new Note(owner, "a".repeat(499), NoteColour.YELLOW, 0, 0));
    assertDoesNotThrow(() -> new Note(owner, "a".repeat(500), NoteColour.YELLOW, 0, 0));
  }

  @Test
  @DisplayName("501 characters are NOTE_TOO_LONG")
  void textOverTheLimit() {
    // Act
    StudyMatchException exception = assertThrows(StudyMatchException.class,
        () -> new Note(owner, "a".repeat(501), NoteColour.YELLOW, 0, 0));

    // Assert
    assertEquals(ErrorCode.NOTE_TOO_LONG, exception.getCode());
  }

  @Test
  @DisplayName("500 characters with spaces around them still fit")
  void spacesDontCount() {
    // Act + Assert
    assertDoesNotThrow(() -> new Note(owner, "  " + "a".repeat(500) + "  ", NoteColour.YELLOW, 0, 0));
  }

  @Test
  @DisplayName("0 and 100 are on the board, for x and y")
  void positionEdges() {
    // Act + Assert
    assertDoesNotThrow(() -> new Note(owner, "", NoteColour.YELLOW, 0, 0));
    assertDoesNotThrow(() -> new Note(owner, "", NoteColour.YELLOW, 100, 100));
    assertDoesNotThrow(() -> new Note(owner, "", NoteColour.YELLOW, 0.01, 99.99));
  }

  @Test
  @DisplayName("Just below 0 or just above 100 is INVALID_NOTE_POSITION")
  void positionOffTheBoard() {
    // Act + Assert
    assertEquals(ErrorCode.INVALID_NOTE_POSITION, assertThrows(StudyMatchException.class,
        () -> new Note(owner, "", NoteColour.YELLOW, -0.01, 50)).getCode());
    assertEquals(ErrorCode.INVALID_NOTE_POSITION, assertThrows(StudyMatchException.class,
        () -> new Note(owner, "", NoteColour.YELLOW, 50, -0.01)).getCode());
    assertEquals(ErrorCode.INVALID_NOTE_POSITION, assertThrows(StudyMatchException.class,
        () -> new Note(owner, "", NoteColour.YELLOW, 100.01, 50)).getCode());
    assertEquals(ErrorCode.INVALID_NOTE_POSITION, assertThrows(StudyMatchException.class,
        () -> new Note(owner, "", NoteColour.YELLOW, 50, 100.01)).getCode());
  }

  @Test
  @DisplayName("Not a number isn't a position either")
  void positionNotANumber() {
    // Act + Assert
    assertThrows(StudyMatchException.class, () -> new Note(owner, "", NoteColour.YELLOW, Double.NaN, 50));
  }

  @Test
  @DisplayName("Moving a note off the board leaves it where it was")
  void failedMoveKeepsPosition() {
    // Arrange
    Note note = new Note(owner, "", NoteColour.YELLOW, 10, 20);

    // Act
    assertThrows(StudyMatchException.class, () -> note.moveTo(150, 20));

    // Assert
    assertEquals(10, note.getX());
    assertEquals(20, note.getY());
  }

  @Test
  @DisplayName("Changing the text, colour or position moves the change date forward")
  void changesTouchTheNote() throws InterruptedException {
    // Arrange
    Note note = new Note(owner, "", NoteColour.YELLOW, 10, 20);
    var created = note.getUpdatedAt();
    Thread.sleep(5);

    // Act
    note.setColour(NoteColour.GREEN);

    // Assert
    assertEquals(NoteColour.GREEN, note.getColour());
    assertFalse(note.getUpdatedAt().isBefore(created));
    assertEquals(created, note.getCreatedAt());
  }
}
