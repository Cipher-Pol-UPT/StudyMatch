package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import com.studymatch.dtos.RoomRequestDTO;
import com.studymatch.enums.RoomType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Room;
import com.studymatch.repositories.RoomRepository;

class RoomServiceTest {

  private static final Long ID = 1L;

  private RoomRepository roomRepository;
  private RoomService roomService;

  @BeforeEach
  void setUp() {
    roomRepository = mock(RoomRepository.class);
    roomService = new RoomService(roomRepository);

    // save returns what it was given
    when(roomRepository.saveAndFlush(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  private static RoomRequestDTO request(String code, int floor, int x, int y, int width, int depth) {
    return new RoomRequestDTO(code, "Building A", floor, RoomType.CLASSROOM, 30, x, y, width, depth);
  }

  private static Room room(String code, int floor, int x, int y, int width, int depth) {
    return new Room(code, "Building A", floor, RoomType.CLASSROOM, 30, x, y, width, depth);
  }

  // what the floor query returns for Building A, floor 1
  private void floorHas(Room... rooms) {
    when(roomRepository.findByBuildingIgnoreCaseAndFloorAndActiveTrue("Building A", 1)).thenReturn(List.of(rooms));
  }

  // register room

  @Test
  @DisplayName("A new room is saved with its data, the code and building without spaces around them")
  void newRoomIsSaved() {
    // Act
    Room room = roomService.registerRoom(
        new RoomRequestDTO("  A.101 ", " Building A  ", 1, RoomType.LAB, 24, 10, 20, 30, 15));

    // Assert
    assertEquals("A.101", room.getCode());
    assertEquals("Building A", room.getBuilding());
    assertEquals(1, room.getFloor());
    assertEquals(RoomType.LAB, room.getType());
    assertEquals(24, room.getCapacity());
    assertEquals(List.of(10, 20, 30, 15), List.of(room.getX(), room.getY(), room.getWidth(), room.getDepth()));
    assertTrue(room.isActive());
    verify(roomRepository).existsByCodeIgnoreCase("A.101");
    verify(roomRepository).saveAndFlush(room);
  }

  @Test
  @DisplayName("The code in lower case is locked before it's checked, then the floor before its rooms are read")
  void locksComeFirst() {
    // Act
    roomService.registerRoom(request("A.101", 1, 0, 0, 10, 10));

    // Assert
    InOrder order = inOrder(roomRepository);
    order.verify(roomRepository).lock("code|a.101");
    order.verify(roomRepository).existsByCodeIgnoreCase("A.101");
    order.verify(roomRepository).lock("floor|building a|1");
    order.verify(roomRepository).findByBuildingIgnoreCaseAndFloorAndActiveTrue("Building A", 1);
    order.verify(roomRepository).saveAndFlush(any());
  }

  @Test
  @DisplayName("A code that already exists gets ROOM_ALREADY_EXISTS and the room isn't saved")
  void codeTaken() {
    // Arrange
    when(roomRepository.existsByCodeIgnoreCase("A.101")).thenReturn(true);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> roomService.registerRoom(request("A.101", 1, 0, 0, 10, 10)));

    // Assert
    assertEquals(ErrorCode.ROOM_ALREADY_EXISTS, e.getCode());
    verify(roomRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("The same code saved by someone else at the same moment is ROOM_ALREADY_EXISTS, not a crash")
  void codeTakenAtTheSameTime() {
    // Arrange
    when(roomRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> roomService.registerRoom(request("A.101", 1, 0, 0, 10, 10)));

    // Assert
    assertEquals(ErrorCode.ROOM_ALREADY_EXISTS, e.getCode());
  }

  @Test
  @DisplayName("A room over an active room on the same floor gets ROOM_OVERLAP naming it, and isn't saved")
  void overlapIsRefused() {
    // Arrange
    floorHas(room("A.102", 1, 20, 0, 20, 20));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> roomService.registerRoom(request("A.101", 1, 10, 0, 20, 20)));

    // Assert
    assertEquals(ErrorCode.ROOM_OVERLAP, e.getCode());
    assertEquals(Map.of("code", "A.102"), e.getParams());
    verify(roomRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("A room next to another one, sharing its edge, is saved")
  void touchingIsFine() {
    // Arrange
    floorHas(room("A.102", 1, 20, 0, 20, 20));

    // Act
    Room room = roomService.registerRoom(request("A.101", 1, 0, 0, 20, 20));

    // Assert
    verify(roomRepository).saveAndFlush(room);
  }

  @Test
  @DisplayName("A room going past the edge of the grid gets ROOM_OUTSIDE_FLOOR with the grid size")
  void outsideFloor() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> roomService.registerRoom(request("A.101", 1, 90, 0, 11, 10)));

    // Assert
    assertEquals(ErrorCode.ROOM_OUTSIDE_FLOOR, e.getCode());
    assertEquals(Map.of("size", Room.GRID_SIZE), e.getParams());
    verify(roomRepository, never()).saveAndFlush(any());
  }

  // edit room

  @Test
  @DisplayName("Editing saves every field, and the room's own old position isn't an overlap")
  void editMovesRoom() {
    // Arrange
    Room room = room("A.101", 1, 0, 0, 20, 20);
    when(roomRepository.findById(ID)).thenReturn(Optional.of(room));
    floorHas(room);

    // Act
    Room edited = roomService.editRoom(ID,
        new RoomRequestDTO(" A.101b ", "Building A", 1, RoomType.LAB, 25, 10, 10, 20, 20));

    // Assert
    assertSame(room, edited);
    assertEquals("A.101b", room.getCode());
    assertEquals(RoomType.LAB, room.getType());
    assertEquals(25, room.getCapacity());
    assertEquals(10, room.getX());
    verify(roomRepository).existsByCodeIgnoreCaseAndIdNot("A.101b", ID);
    verify(roomRepository).saveAndFlush(room);
  }

  @Test
  @DisplayName("Moving to another floor locks and checks that floor")
  void editToOtherFloor() {
    // Arrange
    Room room = room("A.101", 1, 0, 0, 20, 20);
    when(roomRepository.findById(ID)).thenReturn(Optional.of(room));
    when(roomRepository.findByBuildingIgnoreCaseAndFloorAndActiveTrue("Building A", 2))
        .thenReturn(List.of(room("A.201", 2, 0, 0, 20, 20)));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> roomService.editRoom(ID, request("A.101", 2, 0, 0, 20, 20)));

    // Assert
    assertEquals(ErrorCode.ROOM_OVERLAP, e.getCode());
    verify(roomRepository).lock("floor|building a|2");
  }

  @Test
  @DisplayName("Another room's code gets ROOM_ALREADY_EXISTS and the room isn't saved")
  void editCodeTaken() {
    // Arrange
    Room room = room("A.101", 1, 0, 0, 20, 20);
    when(roomRepository.findById(ID)).thenReturn(Optional.of(room));
    when(roomRepository.existsByCodeIgnoreCaseAndIdNot("A.102", ID)).thenReturn(true);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> roomService.editRoom(ID, request("A.102", 1, 0, 0, 20, 20)));

    // Assert
    assertEquals(ErrorCode.ROOM_ALREADY_EXISTS, e.getCode());
    assertEquals("A.101", room.getCode());
    verify(roomRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("An inactive room can be edited over an active one, it takes no space until it's activated")
  void editInactiveRoom() {
    // Arrange
    Room room = room("A.101", 1, 0, 0, 20, 20);
    room.setActive(false);
    when(roomRepository.findById(ID)).thenReturn(Optional.of(room));
    floorHas(room("A.102", 1, 0, 0, 20, 20));

    // Act
    roomService.editRoom(ID, request("A.101", 1, 0, 0, 20, 20));

    // Assert
    verify(roomRepository).lock("code|a.101");
    verify(roomRepository, never()).lock(startsWith("floor|"));
    verify(roomRepository).saveAndFlush(room);
  }

  @Test
  @DisplayName("Editing a room that doesn't exist is NOT_FOUND")
  void editUnknownRoom() {
    // Arrange
    when(roomRepository.findById(ID)).thenReturn(Optional.empty());

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> roomService.editRoom(ID, request("A.101", 1, 0, 0, 20, 20)));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  // deactivate and activate

  @Test
  @DisplayName("Deactivating marks the room inactive and keeps it")
  void deactivate() {
    // Arrange
    Room room = room("A.101", 1, 0, 0, 20, 20);
    when(roomRepository.findById(ID)).thenReturn(Optional.of(room));

    // Act
    roomService.deactivateRoom(ID);

    // Assert
    assertFalse(room.isActive());
    verify(roomRepository).save(room);
    verify(roomRepository, never()).delete(any());
  }

  @Test
  @DisplayName("Activating a room whose place is free makes it active again")
  void activate() {
    // Arrange
    Room room = room("A.101", 1, 0, 0, 20, 20);
    room.setActive(false);
    when(roomRepository.findById(ID)).thenReturn(Optional.of(room));
    floorHas(room("A.102", 1, 20, 0, 20, 20));

    // Act
    roomService.activateRoom(ID);

    // Assert
    assertTrue(room.isActive());
    verify(roomRepository).lock("floor|building a|1");
    verify(roomRepository).save(room);
  }

  @Test
  @DisplayName("Activating a room whose place was taken meanwhile gets ROOM_OVERLAP and it stays inactive")
  void activateTakenPlace() {
    // Arrange
    Room room = room("A.101", 1, 0, 0, 20, 20);
    room.setActive(false);
    when(roomRepository.findById(ID)).thenReturn(Optional.of(room));
    floorHas(room("A.102", 1, 10, 10, 20, 20));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> roomService.activateRoom(ID));

    // Assert
    assertEquals(ErrorCode.ROOM_OVERLAP, e.getCode());
    assertEquals(Map.of("code", "A.102"), e.getParams());
    assertFalse(room.isActive());
    verify(roomRepository, never()).save(any());
  }

  @Test
  @DisplayName("Activating a room that's already active changes nothing")
  void activateActiveRoom() {
    // Arrange
    Room room = room("A.101", 1, 0, 0, 20, 20);
    when(roomRepository.findById(ID)).thenReturn(Optional.of(room));

    // Act
    roomService.activateRoom(ID);

    // Assert
    assertTrue(room.isActive());
    verify(roomRepository, never()).lock(anyString());
    verify(roomRepository, never()).save(any());
  }

  // list

  @Test
  @DisplayName("The list comes from the repository in building, floor and code order")
  void listRooms() {
    // Arrange
    List<Room> rooms = List.of(room("A.101", 1, 0, 0, 10, 10));
    when(roomRepository.findAllByOrderByBuildingAscFloorAscCodeAsc()).thenReturn(rooms);

    // Act + Assert
    assertEquals(rooms, roomService.listRooms());
  }

}
