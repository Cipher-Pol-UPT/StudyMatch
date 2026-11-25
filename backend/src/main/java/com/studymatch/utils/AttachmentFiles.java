package com.studymatch.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

import org.apache.commons.compress.archivers.zip.ZipFile;

// what an attachment says it is against what's in it (GR10)
public final class AttachmentFiles {

  private static final byte[] PNG = HexFormat.of().parseHex("89504e470d0a1a0a");
  private static final byte[] JPEG = HexFormat.of().parseHex("ffd8ff");
  private static final byte[] ZIP = HexFormat.of().parseHex("504b0304");

  private AttachmentFiles() {
  }

  // by the file's first bytes, an Office file is a zip with its own parts and plain text has nothing to look for
  public static boolean matches(AttachmentType type, byte[] bytes) {
    return switch (type) {
      case PNG -> hasAt(bytes, 0, PNG);
      case JPEG -> hasAt(bytes, 0, JPEG);
      case GIF -> hasAt(bytes, 0, ascii("GIF87a")) || hasAt(bytes, 0, ascii("GIF89a"));
      case WEBP -> hasAt(bytes, 0, ascii("RIFF")) && hasAt(bytes, 8, ascii("WEBP"));
      case PDF -> hasAt(bytes, 0, ascii("%PDF-"));
      case DOCX -> isOffice(bytes, "word/document.xml");
      case XLSX -> isOffice(bytes, "xl/workbook.xml");
      case PPTX -> isOffice(bytes, "ppt/presentation.xml");
      case TXT -> isText(bytes);
    };
  }

  // without the folders some browsers send, and what can't be seen: controls (the database refuses some) and the
  // marks that turn the text around, to make a name look like another type
  public static String cleanName(String name) {
    return name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1)
        .replaceAll("[\\p{Cc}\\p{Cf}\\p{Cs}\\p{Zl}\\p{Zp}]", "")
        .strip();
  }

  // a long name loses the end of its start, not its extension
  public static String cutName(String name, int maxLength) {
    if (name.length() <= maxLength) {
      return name;
    }
    String extension = name.substring(name.lastIndexOf('.'));
    int end = maxLength - extension.length();
    // not through the middle of an emoji
    if (Character.isHighSurrogate(name.charAt(end - 1))) {
      end--;
    }
    return name.substring(0, end) + extension;
  }

  // only the zip's index is read, nothing is unzipped, so a small zip of something huge costs nothing
  private static boolean isOffice(byte[] bytes, String mainPart) {
    if (!hasAt(bytes, 0, ZIP)) {
      return false;
    }
    try (ZipFile zip = ZipFile.builder().setByteArray(bytes).setIgnoreLocalFileHeader(true).get()) {
      return zip.getEntry("[Content_Types].xml") != null && zip.getEntry(mainPart) != null;
    } catch (IOException notAZip) {
      return false;
    }
  }

  // UTF-8 without control characters, besides tabs and line ends
  private static boolean isText(byte[] bytes) {
    try {
      String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
      return !text.isEmpty() && text.chars().noneMatch(AttachmentFiles::isBinary);
    } catch (CharacterCodingException notUtf8) {
      return false;
    }
  }

  private static boolean isBinary(int character) {
    return character < ' ' && "\t\n\r\f".indexOf(character) < 0;
  }

  private static boolean hasAt(byte[] bytes, int from, byte[] signature) {
    return bytes.length >= from + signature.length
        && Arrays.equals(bytes, from, from + signature.length, signature, 0, signature.length);
  }

  private static byte[] ascii(String text) {
    return text.getBytes(StandardCharsets.US_ASCII);
  }

}
