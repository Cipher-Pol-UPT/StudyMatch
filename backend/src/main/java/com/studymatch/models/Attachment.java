package com.studymatch.models;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// a file sent with a message, never changed. what's in it is in AttachmentFile, so a list of messages doesn't load it.
// the index is for finding a message's, to list or delete them
@Entity
@Table(indexes = @Index(columnList = "message_id"))
public class Attachment {

  public static final int MAX_NAME_LENGTH = 255;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // the database deletes it with the message (GR10)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "message_id")
  @OnDelete(action = OnDeleteAction.CASCADE)
  @NotNull
  private Message message;

  @NotBlank
  @Size(max = MAX_NAME_LENGTH)
  private String name;

  @NotBlank
  private String contentType;

  // in bytes
  @Positive
  private int size;

  protected Attachment() {
  }

  public Attachment(Message message, String name, String contentType, int size) {
    this.message = message;
    this.name = name;
    this.contentType = contentType;
    this.size = size;
  }

  public Long getId() {
    return id;
  }

  public Message getMessage() {
    return message;
  }

  public String getName() {
    return name;
  }

  public String getContentType() {
    return contentType;
  }

  public int getSize() {
    return size;
  }

}
