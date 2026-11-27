package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.studymatch.enums.RoomType;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class RoomRequestDTOTest {

  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  // field -> annotation that failed, e.g. code=NotBlank
  private Set<String> failures(RoomRequestDTO request) {
    return validator.validate(request).stream()
        .map(v -> v.getPropertyPath() + "=" + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
        .collect(Collectors.toSet());
  }

  private static RoomRequestDTO withCode(String code) {
    return new RoomRequestDTO(code, "Building A", 1, RoomType.CLASSROOM, 30, 0, 0, 10, 10);
  }

  private static RoomRequestDTO withBuilding(String building) {
    return new RoomRequestDTO("A.101", building, 1, RoomType.CLASSROOM, 30, 0, 0, 10, 10);
  }

  private static RoomRequestDTO withFloorAndCapacity(Integer floor, Integer capacity) {
    return new RoomRequestDTO("A.101", "Building A", floor, RoomType.CLASSROOM, capacity, 0, 0, 10, 10);
  }

  private static RoomRequestDTO withPosition(Integer x, Integer y, Integer width, Integer depth) {
    return new RoomRequestDTO("A.101", "Building A", 1, RoomType.CLASSROOM, 30, x, y, width, depth);
  }

  @Test
  @DisplayName("A normal room is accepted")
  void validRoom() {
    assertTrue(failures(withCode("A.101")).isEmpty());
  }

  @ParameterizedTest(name = "\"{0}\" is rejected")
  @ValueSource(strings = { "", "   " })
  @DisplayName("An empty or only-spaces code or building is NOT_BLANK")
  void blankCodeAndBuilding(String value) {
    assertEquals(Set.of("code=NotBlank"), failures(withCode(value)));
    assertEquals(Set.of("building=NotBlank"), failures(withBuilding(value)));
  }

  @Test
  @DisplayName("A code over 20 characters is too long (boundary: 20 ok, 21 fails)")
  void codeLength() {
    assertTrue(failures(withCode("a".repeat(20))).isEmpty());
    assertEquals(Set.of("code=Size"), failures(withCode("a".repeat(21))));
  }

  @Test
  @DisplayName("A building over 50 characters is too long (boundary: 50 ok, 51 fails)")
  void buildingLength() {
    assertTrue(failures(withBuilding("a".repeat(50))).isEmpty());
    assertEquals(Set.of("building=Size"), failures(withBuilding("a".repeat(51))));
  }

  @Test
  @DisplayName("Floors go from -5 to 50 (boundaries: -6 fails Min, 51 fails Max)")
  void floorRange() {
    assertTrue(failures(withFloorAndCapacity(-5, 30)).isEmpty());
    assertTrue(failures(withFloorAndCapacity(50, 30)).isEmpty());
    assertEquals(Set.of("floor=Min"), failures(withFloorAndCapacity(-6, 30)));
    assertEquals(Set.of("floor=Max"), failures(withFloorAndCapacity(51, 30)));
  }

  @Test
  @DisplayName("Capacity goes from 1 to 1000 (boundaries: 0 fails Min, 1001 fails Max)")
  void capacityRange() {
    assertTrue(failures(withFloorAndCapacity(1, 1)).isEmpty());
    assertTrue(failures(withFloorAndCapacity(1, 1000)).isEmpty());
    assertEquals(Set.of("capacity=Min"), failures(withFloorAndCapacity(1, 0)));
    assertEquals(Set.of("capacity=Max"), failures(withFloorAndCapacity(1, 1001)));
  }

  @Test
  @DisplayName("x and y go from 0 to 99 (boundaries: -1 fails Min, 100 fails Max)")
  void positionRange() {
    assertTrue(failures(withPosition(0, 0, 1, 1)).isEmpty());
    assertTrue(failures(withPosition(99, 99, 1, 1)).isEmpty());
    assertEquals(Set.of("x=Min", "y=Min"), failures(withPosition(-1, -1, 1, 1)));
    assertEquals(Set.of("x=Max", "y=Max"), failures(withPosition(100, 100, 1, 1)));
  }

  @Test
  @DisplayName("Width and depth go from 1 to 100 (boundaries: 0 fails Min, 101 fails Max)")
  void sizeRange() {
    assertTrue(failures(withPosition(0, 0, 1, 1)).isEmpty());
    assertTrue(failures(withPosition(0, 0, 100, 100)).isEmpty());
    assertEquals(Set.of("width=Min", "depth=Min"), failures(withPosition(0, 0, 0, 0)));
    assertEquals(Set.of("width=Max", "depth=Max"), failures(withPosition(0, 0, 101, 101)));
  }

  @Test
  @DisplayName("Missing numbers and type are NOT_NULL, a missing code or building is NOT_BLANK")
  void missingFields() {
    assertEquals(Set.of("code=NotBlank", "building=NotBlank", "floor=NotNull", "type=NotNull", "capacity=NotNull",
        "x=NotNull", "y=NotNull", "width=NotNull", "depth=NotNull"),
        failures(new RoomRequestDTO(null, null, null, null, null, null, null, null, null)));
  }

}
