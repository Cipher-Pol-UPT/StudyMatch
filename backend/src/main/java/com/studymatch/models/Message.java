package com.studymatch.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// deleting a message looks for its replies, the foreign key alone has no index (#218).
// and a chat's pages and search go through its messages by id, without reading the other chats' (#220)
@Entity
@Table(indexes = { @Index(columnList = "reply_to_id"), @Index(columnList = "chat_id, id") })
public class Message {

  public static final int MAX_TEXT_LENGTH = 2000;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @NotNull
  private Chat chat;

  @ManyToOne
  @NotNull
  private User author;

  // empty when it only has attachments (#210), the service makes sure it has something
  @NotNull
  @Size(max = MAX_TEXT_LENGTH)
  @Column(length = MAX_TEXT_LENGTH)
  private String text;

  @NotNull
  private LocalDateTime sentAt;

  // null until edited, then the message shows "(edited)" (GR10)
  private LocalDateTime editedAt;

  // the blocker, when a blocked user sent it in their DM. stays hidden from them even after unblocking (GR9)
  @ManyToOne
  private User hiddenFrom;

  // the database clears it when the original is deleted. lazy or it'd load a whole chain of replies, and never
  // updated, editing the reply writes the whole row and would put a deleted original back (#218)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reply_to_id", updatable = false)
  @OnDelete(action = OnDeleteAction.SET_NULL)
  private Message replyTo;

  // stays true when the original is deleted, so it can say so. the default is for the messages from before
  @ColumnDefault("false")
  private boolean reply;

  // the database deletes them with it, so nothing is cascaded here. a list of messages loads theirs in one query
  @OneToMany(mappedBy = "message")
  @OrderBy("id")
  @BatchSize(size = 100)
  private List<Attachment> attachments = new ArrayList<>();

  protected Message() {
  }

  public Message(Chat chat, User author, String text) {
    this.chat = chat;
    this.author = author;
    this.text = text;
    this.sentAt = LocalDateTime.now();
  }

  // the original is in the same chat, the service looked it up there
  public Message(Chat chat, User author, String text, Message replyTo) {
    this(chat, author, text);
    this.replyTo = replyTo;
    this.reply = replyTo != null;
  }

  public Long getId() {
    return id;
  }

  public Chat getChat() {
    return chat;
  }

  public User getAuthor() {
    return author;
  }

  public String getText() {
    return text;
  }

  public LocalDateTime getSentAt() {
    return sentAt;
  }

  public LocalDateTime getEditedAt() {
    return editedAt;
  }

  public boolean isEdited() {
    return editedAt != null;
  }

  public void edit(String text) {
    this.text = text;
    this.editedAt = LocalDateTime.now();
  }

  public User getHiddenFrom() {
    return hiddenFrom;
  }

  public void hideFrom(User blocker) {
    this.hiddenFrom = blocker;
  }

  public Message getReplyTo() {
    return replyTo;
  }

  public boolean isReply() {
    return reply;
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }

  public void addAttachment(Attachment attachment) {
    attachments.add(attachment);
  }

  // the original was hidden from a blocker and this reply isn't, so not everyone may see the quote (GR9)
  public boolean quotesHidden() {
    return replyTo != null && replyTo.getHiddenFrom() != null;
  }

  // by number, the user may come from another request than the message
  public boolean isHiddenFrom(User user) {
    return hiddenFrom != null && hiddenFrom.getNumber().equals(user.getNumber());
  }

}
