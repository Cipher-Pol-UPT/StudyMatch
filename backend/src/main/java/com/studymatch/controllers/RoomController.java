package com.studymatch.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.RoomRequestDTO;
import com.studymatch.dtos.RoomResponseDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.Room;
import com.studymatch.services.RoomService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/rooms/")
@Tag(name = "Rooms", description = "The university's rooms, their type, capacity and place on their floor")
public class RoomController {

  private final RoomService roomService;

  public RoomController(RoomService roomService) {
    this.roomService = roomService;
  }

  // any logged-in user: Staff manage them, the campus map (#368) shows them to everyone
  @GetMapping
  @Operation(summary = "Every room, inactive ones included, by building, floor and code")
  public ResponseEntity<List<RoomResponseDTO>> listRooms() {
    return ResponseEntity.ok(roomService.listRooms().stream().map(RoomResponseDTO::from).toList());
  }

  @GetMapping("{id}")
  @Operation(summary = "One room")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<RoomResponseDTO> getRoom(@PathVariable Long id) {
    return ResponseEntity.ok(RoomResponseDTO.from(roomService.getRoom(id)));
  }

  @PostMapping
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Register a room")
  @ErrorCodes({ ErrorCode.ROOM_ALREADY_EXISTS, ErrorCode.ROOM_OVERLAP, ErrorCode.ROOM_OUTSIDE_FLOOR })
  public ResponseEntity<RoomResponseDTO> registerRoom(@Valid @RequestBody RoomRequestDTO registerRequest) {
    Room room = roomService.registerRoom(registerRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(RoomResponseDTO.from(room));
  }

  @PutMapping("{id}")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Edit a room")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.ROOM_ALREADY_EXISTS, ErrorCode.ROOM_OVERLAP,
      ErrorCode.ROOM_OUTSIDE_FLOOR })
  public ResponseEntity<RoomResponseDTO> editRoom(@PathVariable Long id,
      @Valid @RequestBody RoomRequestDTO editRequest) {
    return ResponseEntity.ok(RoomResponseDTO.from(roomService.editRoom(id, editRequest)));
  }

  @PutMapping("{id}/deactivate")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Deactivate a room, what already uses it keeps it")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<RoomResponseDTO> deactivateRoom(@PathVariable Long id) {
    return ResponseEntity.ok(RoomResponseDTO.from(roomService.deactivateRoom(id)));
  }

  @PutMapping("{id}/activate")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Activate a room again, if nothing took its place meanwhile")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.ROOM_OVERLAP })
  public ResponseEntity<RoomResponseDTO> activateRoom(@PathVariable Long id) {
    return ResponseEntity.ok(RoomResponseDTO.from(roomService.activateRoom(id)));
  }

}
