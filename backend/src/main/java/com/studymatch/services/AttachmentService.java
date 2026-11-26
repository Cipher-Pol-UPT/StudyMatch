package com.studymatch.services;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.studymatch.dtos.AttachmentFileDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Attachment;
import com.studymatch.models.AttachmentFile;
import com.studymatch.models.Message;
import com.studymatch.repositories.AttachmentFileRepository;
import com.studymatch.utils.AttachmentFiles;
import com.studymatch.utils.AttachmentType;

@Service
public class AttachmentService {

  private static final int MAX_ATTACHMENTS = 5;
  private static final int MAX_MEGABYTES = 10;

  private final AttachmentFileRepository attachmentFileRepository;

  public AttachmentService(AttachmentFileRepository attachmentFileRepository) {
    this.attachmentFileRepository = attachmentFileRepository;
  }

  // to a message that was just saved, in the order they came. one bad file refuses the whole message (GR10)
  @Transactional
  public void attach(Message message, List<MultipartFile> files) {
    if (files.size() > MAX_ATTACHMENTS) {
      throw new StudyMatchException(ErrorCode.TOO_MANY_ATTACHMENTS, Map.of("max", MAX_ATTACHMENTS));
    }
    List<AttachmentFile> attached = new ArrayList<>();
    for (int index = 0; index < files.size(); index++) {
      attached.add(read(message, files.get(index), index));
    }
    attachmentFileRepository.saveAll(attached);
    attached.forEach(file -> message.addAttachment(file.getAttachment()));
  }

  // the message checks are in MessageService, here it only has to be one of its files
  @Transactional(readOnly = true)
  public AttachmentFileDTO open(Message message, Long attachmentId) {
    return attachmentFileRepository.findByIdAndMessage(attachmentId, message)
        .map(AttachmentFileDTO::from)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  // the errors have the index too, pasted images all have the same name
  private static AttachmentFile read(Message message, MultipartFile file, int index) {
    String originalName = file.getOriginalFilename();
    String name = AttachmentFiles.cleanName(originalName == null ? "" : originalName);
    AttachmentType type = AttachmentType.ofName(name)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED,
            Map.of("name", name, "index", index)));
    if (file.getSize() > MAX_MEGABYTES * 1024 * 1024) {
      throw new StudyMatchException(ErrorCode.ATTACHMENT_TOO_LARGE,
          Map.of("name", name, "index", index, "max", MAX_MEGABYTES));
    }
    byte[] bytes = bytesOf(file);
    if (!AttachmentFiles.matches(type, bytes)) {
      throw new StudyMatchException(ErrorCode.ATTACHMENT_CONTENT_MISMATCH, Map.of("name", name, "index", index));
    }
    String shortName = AttachmentFiles.cutName(name, Attachment.MAX_NAME_LENGTH);
    return new AttachmentFile(new Attachment(message, shortName, type.getContentType(), bytes.length), bytes);
  }

  private static byte[] bytesOf(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

}
