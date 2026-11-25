package com.studymatch.models;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.NotNull;

// what's in an attachment, on its own table and with the same id. only read when someone opens it.
// immutable, or Hibernate keeps a second copy of the bytes to see if they changed
@Entity
@Immutable
public class AttachmentFile {

  @Id
  private Long id;

  // saved with it, and the database deletes it with it
  @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
  @MapsId
  @JoinColumn(name = "attachment_id")
  @OnDelete(action = OnDeleteAction.CASCADE)
  @NotNull
  private Attachment attachment;

  @NotNull
  private byte[] bytes;

  protected AttachmentFile() {
  }

  public AttachmentFile(Attachment attachment, byte[] bytes) {
    this.attachment = attachment;
    this.bytes = bytes;
  }

  public Long getId() {
    return id;
  }

  public Attachment getAttachment() {
    return attachment;
  }

  public byte[] getBytes() {
    return bytes;
  }

}
