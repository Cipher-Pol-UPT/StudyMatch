package com.studymatch.utils;

import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// pictures saved as "data:image/png;base64,...", static so the models can check them in their setters
public final class ImageDataUrls {

  private static final Pattern IMAGE_DATA_URL = Pattern.compile("^data:image/(png|jpeg);base64,([A-Za-z0-9+/=]+)$");

  private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 0x50, 0x4E, 0x47 };

  private static final byte[] JPEG_SIGNATURE = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };

  private ImageDataUrls() {
  }

  // a PNG or JPEG up to maxBytes, the first bytes have to match the type it says it is
  public static boolean isValidImage(String image, int maxBytes) {
    Matcher dataUrl = IMAGE_DATA_URL.matcher(image);
    if (!dataUrl.matches()) {
      return false;
    }

    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(dataUrl.group(2));
    } catch (IllegalArgumentException invalidBase64) {
      return false;
    }

    byte[] expectedSignature = dataUrl.group(1).equals("png") ? PNG_SIGNATURE : JPEG_SIGNATURE;
    return bytes.length <= maxBytes && startsWith(bytes, expectedSignature);
  }

  private static boolean startsWith(byte[] bytes, byte[] signature) {
    return bytes.length >= signature.length
        && Arrays.equals(bytes, 0, signature.length, signature, 0, signature.length);
  }

}
