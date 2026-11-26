package com.studymatch.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Note;

public interface NoteRepository extends JpaRepository<Note, Long> {

  // oldest change first, so the last one touched is drawn on top
  List<Note> findByOwner_NumberOrderByUpdatedAtAscIdAsc(Long ownerNumber);

  // someone else's note looks the same as one that doesn't exist
  Optional<Note> findByIdAndOwner_Number(Long id, Long ownerNumber);

  long countByOwner_Number(Long ownerNumber);

}
