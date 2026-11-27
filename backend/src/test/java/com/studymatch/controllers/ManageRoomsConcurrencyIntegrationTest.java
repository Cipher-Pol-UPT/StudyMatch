package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.studymatch.dtos.RoomRequestDTO;
import com.studymatch.enums.RoomType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Room;
import com.studymatch.repositories.RoomRepository;
import com.studymatch.services.RoomService;

// two Staff saving rooms at once. real commits, so it cleans up after itself
@SpringBootTest
@ActiveProfiles("test")
class ManageRoomsConcurrencyIntegrationTest {

  private static final String BUILDING = "IT Concurrent Building";

  // long enough for the other thread to reach the lock and be seen waiting
  private static final long WAITING_MILLIS = 700;

  @Autowired
  private RoomService roomService;

  @Autowired
  private RoomRepository roomRepository;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private final ExecutorService otherThread = Executors.newSingleThreadExecutor();
  private TransactionTemplate transaction;

  @BeforeEach
  void setUp() {
    transaction = new TransactionTemplate(transactionManager);
  }

  @AfterEach
  void cleanUp() {
    otherThread.shutdownNow();
    roomRepository.deleteAll(roomsInBuilding());
  }

  private List<Room> roomsInBuilding() {
    return roomRepository.findAll().stream().filter(room -> room.getBuilding().equals(BUILDING)).toList();
  }

  private Runnable register(String code, int floor, int x) {
    return () -> roomService.registerRoom(new RoomRequestDTO(code, BUILDING, floor, RoomType.CLASSROOM, 30, x, 0, 20, 20));
  }

  // the first is saved but not committed when the second starts, which has to wait for it
  private Future<?> during(Runnable first, Runnable second) {
    Future<?>[] other = new Future<?>[1];
    transaction.executeWithoutResult(status -> {
      first.run();
      other[0] = otherThread.submit(second);
      assertThrows(TimeoutException.class, () -> other[0].get(WAITING_MILLIS, TimeUnit.MILLISECONDS));
    });
    return other[0];
  }

  private static StudyMatchException refusal(Future<?> second) {
    ExecutionException e = assertThrows(ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));
    return assertInstanceOf(StudyMatchException.class, e.getCause());
  }

  @Test
  @DisplayName("B.101 and b.101 on different floors at once: the second waits and is ROOM_ALREADY_EXISTS")
  void sameCodeOtherCase() {
    // Act
    Future<?> second = during(register("ITC.101", 1, 0), register("itc.101", 2, 0));

    // Assert
    assertEquals(ErrorCode.ROOM_ALREADY_EXISTS, refusal(second).getCode());
    assertEquals(List.of("ITC.101"), roomsInBuilding().stream().map(Room::getCode).toList());
  }

  @Test
  @DisplayName("Two overlapping rooms on one floor at once: the second waits and is ROOM_OVERLAP")
  void overlappingAtOnce() {
    // Act
    Future<?> second = during(register("ITC.101", 1, 0), register("ITC.102", 1, 10));

    // Assert
    StudyMatchException refused = refusal(second);
    assertEquals(ErrorCode.ROOM_OVERLAP, refused.getCode());
    assertEquals(Map.of("code", "ITC.101"), refused.getParams());
    assertEquals(1, roomsInBuilding().size());
  }

}
