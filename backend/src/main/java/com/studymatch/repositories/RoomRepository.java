package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {

  boolean existsByCodeIgnoreCase(String code);

  boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

  List<Room> findAllByOrderByBuildingAscFloorAscCodeAsc();

  // what a room on this floor can't overlap, inactive rooms don't take up space
  List<Room> findByBuildingIgnoreCaseAndFloorAndActiveTrue(String building, int floor);

  // no row stands for a floor or for a code in any case, so two saves of one wait on a lock named after it until
  // the first commits. the void function in from gives one row, so there's something to return
  @Query(value = "select 1 from pg_advisory_xact_lock(hashtext(:key))", nativeQuery = true)
  int lock(String key);

}
