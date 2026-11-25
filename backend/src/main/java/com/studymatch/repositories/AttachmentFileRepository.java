package com.studymatch.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.AttachmentFile;
import com.studymatch.models.Message;

// the attachments come and go with their files and messages, so they don't need their own
public interface AttachmentFileRepository extends JpaRepository<AttachmentFile, Long> {

  // a file of that message, an id from another message counts as missing
  @Query("select f from AttachmentFile f join fetch f.attachment a where f.id = :id and a.message = :message")
  Optional<AttachmentFile> findByIdAndMessage(Long id, Message message);

}
