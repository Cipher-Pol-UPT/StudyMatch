package com.studymatch.models;

import java.util.Locale;

import com.studymatch.enums.RoomType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// GR12: never deleted, only deactivated. the position is on its floor's grid, only the campus map (#368) uses it
@Entity
public class Room {

  // each floor is a GRID_SIZE x GRID_SIZE grid
  public static final int GRID_SIZE = 100;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Column(unique = true)
  private String code;

  @NotBlank
  private String building;

  private int floor;

  @NotNull
  @Enumerated(EnumType.STRING)
  private RoomType type;

  @Min(1)
  private int capacity;

  @Min(0)
  private int x;

  @Min(0)
  private int y;

  @Min(1)
  @Max(GRID_SIZE)
  private int width;

  @Min(1)
  @Max(GRID_SIZE)
  private int depth;

  private boolean active = true;

  protected Room() {
  }

  public Room(String code, String building, int floor, RoomType type, int capacity, int x, int y, int width,
      int depth) {
    edit(code, building, floor, type, capacity, x, y, width, depth);
  }

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getBuilding() {
    return building;
  }

  public int getFloor() {
    return floor;
  }

  public RoomType getType() {
    return type;
  }

  public int getCapacity() {
    return capacity;
  }

  public int getX() {
    return x;
  }

  public int getY() {
    return y;
  }

  public int getWidth() {
    return width;
  }

  public int getDepth() {
    return depth;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  // edit room changes everything but whether it's active
  public void edit(String code, String building, int floor, RoomType type, int capacity, int x, int y, int width,
      int depth) {
    this.code = code;
    this.building = building;
    this.floor = floor;
    this.type = type;
    this.capacity = capacity;
    this.x = x;
    this.y = y;
    this.width = width;
    this.depth = depth;
  }

  // "Building A" and "building a" are the same building
  public boolean sameFloorAs(Room other) {
    return building.equalsIgnoreCase(other.building) && floor == other.floor;
  }

  // sharing an edge isn't overlapping: one ending at x=10 and the next starting at x=10 is fine
  public boolean overlaps(Room other) {
    return sameFloorAs(other)
        && x < other.x + other.width && other.x < x + width
        && y < other.y + other.depth && other.y < y + depth;
  }

  public boolean fitsOnFloor() {
    return x + width <= GRID_SIZE && y + depth <= GRID_SIZE;
  }

  public String floorKey() {
    return floorKey(building, floor);
  }

  // what the floor lock is taken on, the same for every spelling of the building
  public static String floorKey(String building, int floor) {
    return building.toLowerCase(Locale.ROOT) + "|" + floor;
  }

}
