package com.studymatch.services;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.RoomRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Room;
import com.studymatch.repositories.RoomRepository;

@Service
public class RoomService {

  private final RoomRepository roomRepository;

  public RoomService(RoomRepository roomRepository) {
    this.roomRepository = roomRepository;
  }

  // "B.101" and " b.101 " are the same room.
  // the code is always locked before the floor, so two saves never wait on each other's lock
  @Transactional
  public Room registerRoom(RoomRequestDTO request) {
    String code = request.code().trim();
    roomRepository.lock(Room.codeKey(code));
    if (roomRepository.existsByCodeIgnoreCase(code)) {
      throw new StudyMatchException(ErrorCode.ROOM_ALREADY_EXISTS);
    }
    String building = request.building().trim();
    List<Room> floor = lockFloor(building, request.floor());

    Room room = new Room(code, building, request.floor(), request.type(), request.capacity(), request.x(), request.y(),
        request.width(), request.depth());
    checkFitsOnFloor(room);
    checkNoOverlap(room, floor);
    return save(room);
  }

  // only the floor it ends up on is locked, leaving a floor can't make anything overlap there.
  // the room is changed after the queries, or hibernate would write it before them and a taken code would fail there
  @Transactional
  public Room editRoom(Long id, RoomRequestDTO request) {
    Room room = getRoom(id);
    String code = request.code().trim();
    roomRepository.lock(Room.codeKey(code));
    if (roomRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
      throw new StudyMatchException(ErrorCode.ROOM_ALREADY_EXISTS);
    }
    String building = request.building().trim();
    List<Room> floor = room.isActive() ? lockFloor(building, request.floor()) : List.of();

    room.edit(code, building, request.floor(), request.type(), request.capacity(), request.x(), request.y(),
        request.width(), request.depth());
    checkFitsOnFloor(room);
    checkNoOverlap(room, floor);
    return save(room);
  }

  // GR12: what already uses the room keeps it, it's only left out of new schedules and sessions
  @Transactional
  public Room deactivateRoom(Long id) {
    Room room = getRoom(id);
    room.setActive(false);
    return roomRepository.save(room);
  }

  // something may have been placed where it was while it was inactive
  @Transactional
  public Room activateRoom(Long id) {
    Room room = getRoom(id);
    if (room.isActive()) {
      return room;
    }
    checkNoOverlap(room, lockFloor(room.getBuilding(), room.getFloor()));
    room.setActive(true);
    return roomRepository.save(room);
  }

  @Transactional(readOnly = true)
  public List<Room> listRooms() {
    return roomRepository.findAllByOrderByBuildingAscFloorAscCodeAsc();
  }

  @Transactional(readOnly = true)
  public Room getRoom(Long id) {
    return roomRepository.findById(id)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  // the active rooms on the floor, read once nobody else can be saving there
  private List<Room> lockFloor(String building, int floor) {
    roomRepository.lock(Room.floorKey(building, floor));
    return roomRepository.findByBuildingIgnoreCaseAndFloorAndActiveTrue(building, floor);
  }

  private static void checkFitsOnFloor(Room room) {
    if (!room.fitsOnFloor()) {
      throw new StudyMatchException(ErrorCode.ROOM_OUTSIDE_FLOOR, Map.of("size", Room.GRID_SIZE));
    }
  }

  // != and not the id: a room being edited comes back from the floor query as the same object, a new one has no id yet
  private static void checkNoOverlap(Room room, List<Room> floor) {
    floor.stream()
        .filter(other -> other != room && room.overlaps(other))
        .findFirst()
        .ifPresent(other -> {
          throw new StudyMatchException(ErrorCode.ROOM_OVERLAP, Map.of("code", other.getCode()));
        });
  }

  // the code lock already stops two saves of one code, the unique column is only there if something skips it.
  // flush so that happens here and not after the response
  private Room save(Room room) {
    try {
      return roomRepository.saveAndFlush(room);
    } catch (DataIntegrityViolationException e) {
      throw new StudyMatchException(ErrorCode.ROOM_ALREADY_EXISTS);
    }
  }

}
