package com.studymatch.dtos;

import com.studymatch.enums.RoomType;
import com.studymatch.models.Room;

public record RoomResponseDTO(Long id, String code, String building, int floor, RoomType type, int capacity, int x,
    int y, int width, int depth, boolean active) {

  public static RoomResponseDTO from(Room room) {
    return new RoomResponseDTO(room.getId(), room.getCode(), room.getBuilding(), room.getFloor(), room.getType(),
        room.getCapacity(), room.getX(), room.getY(), room.getWidth(), room.getDepth(), room.isActive());
  }

}
