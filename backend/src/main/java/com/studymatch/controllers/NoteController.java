package com.studymatch.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.CreateNoteRequestDTO;
import com.studymatch.dtos.NoteDTO;
import com.studymatch.dtos.UpdateNoteRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.User;
import com.studymatch.services.NoteService;
import com.studymatch.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/notes/")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Notes", description = "Private sticky notes on the home page")
public class NoteController {

  private final NoteService noteService;
  private final UserService userService;

  public NoteController(NoteService noteService, UserService userService) {
    this.noteService = noteService;
    this.userService = userService;
  }

  @GetMapping
  @Operation(summary = "Your notes, oldest change first so the last one touched is on top")
  public ResponseEntity<List<NoteDTO>> list(Authentication authentication) {
    return ResponseEntity.ok(noteService.list(me(authentication).getNumber()));
  }

  @PostMapping
  @Operation(summary = "Add a note with a text (up to 500 characters, can be empty), a colour and x and y "
      + "as percentages of the board. At most 20 notes")
  @ErrorCodes({ ErrorCode.NOTE_TOO_LONG, ErrorCode.INVALID_NOTE_POSITION, ErrorCode.NOTE_LIMIT_REACHED })
  public ResponseEntity<NoteDTO> create(@Valid @RequestBody CreateNoteRequestDTO request,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED).body(noteService.create(me(authentication), request));
  }

  @PatchMapping("{id}")
  @Operation(summary = "Change one of your notes' text, colour or position, only what's sent changes. "
      + "Someone else's note is NOT_FOUND")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.NOTE_TOO_LONG, ErrorCode.INVALID_NOTE_POSITION })
  public ResponseEntity<NoteDTO> update(@PathVariable Long id, @RequestBody UpdateNoteRequestDTO request,
      Authentication authentication) {
    return ResponseEntity.ok(noteService.update(me(authentication).getNumber(), id, request));
  }

  @DeleteMapping("{id}")
  @Operation(summary = "Delete one of your notes for good. Someone else's note is NOT_FOUND")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
    noteService.delete(me(authentication).getNumber(), id);
    return ResponseEntity.noContent().build();
  }

  private User me(Authentication authentication) {
    return userService.getByInstitutionalEmail(authentication.getName());
  }

}
