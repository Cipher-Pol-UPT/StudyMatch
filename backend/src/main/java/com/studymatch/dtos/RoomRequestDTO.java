package com.studymatch.dtos;

import com.studymatch.enums.RoomType;
import com.studymatch.models.Room;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// register and edit room. staying inside the grid (x + width, y + depth) is checked in the service
public record RoomRequestDTO(
    @NotBlank @Size(max = 20) String code,
    @NotBlank @Size(max = 50) String building,
    @NotNull @Min(-5) @Max(50) Integer floor,
    @NotNull RoomType type,
    @NotNull @Min(1) @Max(1000) Integer capacity,
    @NotNull @Min(0) @Max(Room.GRID_SIZE - 1) Integer x,
    @NotNull @Min(0) @Max(Room.GRID_SIZE - 1) Integer y,
    @NotNull @Min(1) @Max(Room.GRID_SIZE) Integer width,
    @NotNull @Min(1) @Max(Room.GRID_SIZE) Integer depth) {

}
