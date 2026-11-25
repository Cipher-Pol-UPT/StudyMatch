package com.studymatch.models;

import java.time.LocalDateTime;
import java.util.Map;
import com.studymatch.enums.NoteColour;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

// a private sticky note on someone's home page. x and y are percentages of the board (#303)
@Entity
@Table(indexes = @Index(columnList = "owner_number"))
public class Note {

  public static final int MAX_TEXT_LENGTH = 500;
  public static final int MAX_NOTES = 20;
  public static final double MAX_POSITION = 100;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "owner_number")
  @NotNull
  private User owner;

  @NotNull
  @Column(length = MAX_TEXT_LENGTH)
  private String text;

  @NotNull
  @Enumerated(EnumType.STRING)
  private NoteColour colour;

  private double x;

  private double y;

  @NotNull
  private LocalDateTime createdAt;

  // the last note changed or moved is drawn on top
  @NotNull
  private LocalDateTime updatedAt;

  protected Note() {
  }

  public Note(User owner, String text, NoteColour colour, double x, double y) {
    this.owner = owner;
    setText(text);
    this.colour = colour;
    moveTo(x, y);
    this.createdAt = LocalDateTime.now();
    this.updatedAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public User getOwner() {
    return owner;
  }

  public String getText() {
    return text;
  }

  // spaces around it don't count, like in chats. an empty note is fine
  public void setText(String text) {
    String stripped = text == null ? "" : text.strip();
    if (stripped.length() > MAX_TEXT_LENGTH) {
      throw new StudyMatchException(ErrorCode.NOTE_TOO_LONG, Map.of("max", MAX_TEXT_LENGTH));
    }
    this.text = stripped;
    touch();
  }

  public NoteColour getColour() {
    return colour;
  }

  public void setColour(NoteColour colour) {
    this.colour = colour;
    touch();
  }

  public double getX() {
    return x;
  }

  public double getY() {
    return y;
  }

  public void moveTo(double x, double y) {
    if (!onBoard(x) || !onBoard(y)) {
      throw new StudyMatchException(ErrorCode.INVALID_NOTE_POSITION);
    }
    this.x = x;
    this.y = y;
    touch();
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  private static boolean onBoard(double value) {
    return value >= 0 && value <= MAX_POSITION;
  }

  private void touch() {
    this.updatedAt = LocalDateTime.now();
  }
}
