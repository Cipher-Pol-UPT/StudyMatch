package com.studymatch.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import com.studymatch.enums.Achievement;
import com.studymatch.models.CertificateEvidence;

// the achievement certificates as PDF, laid out like the StudyMatch UI templates
@Component
public class CertificatePdfWriter {

  // who the certificate is for
  public record Holder(String name, Long number, String degree) {
  }

  // one achievement on a certificate, the evidence can be null
  public record Entry(Achievement achievement, LocalDate earnedAt, CertificateEvidence evidence) {
  }

  private record Art(String rarity, String from, String to) {
  }

  // rarity and tile colours, the same as the frontend's catalog.ts
  private static final Map<Achievement, Art> ART = new EnumMap<>(Map.of(
      Achievement.FIRST_STUDYROOM, new Art("common", "#ffeee4", "#ffe0a8"),
      Achievement.DEDICATED_TUTOR, new Art("epic", "#ffe9f2", "#ffc9df"),
      Achievement.TOP_RATED, new Art("epic", "#fff6d6", "#ffe08a"),
      Achievement.MENTOR, new Art("rare", "#f7fbd8", "#e8f59a")));

  private static final Map<String, String> RARITY_COLOURS = Map.of(
      "common", "#8f8fa3", "rare", "#2a9fd6", "epic", "#8b5cf6", "legendary", "#f2a100");

  private static final String SPARKLE = "<svg xmlns=\"http://www.w3.org/2000/svg\" style=\"width:14px;height:14px\" "
      + "viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#4a3df5\" stroke-width=\"2\" stroke-linecap=\"round\" "
      + "stroke-linejoin=\"round\"><path d=\"M9.94 14.06 4 12l5.94-2.06L12 4l2.06 5.94L20 12l-5.94 2.06L12 20z\"/></svg>";

  private static final String CSS = """
      * { margin: 0; padding: 0; }
      body { font-family: 'Geist'; color: #000; background: #f6f6fb; }
      .page { position: relative; overflow: hidden; background: #f6f6fb; }
      .abs { position: absolute; }
      .eyebrow { font-family: 'Geist Mono'; font-size: 10.5px; text-transform: uppercase; letter-spacing: .04em; color: #5a5a55; }
      .eyebrow b { font-weight: 400; color: #000; text-transform: none; letter-spacing: 0; }
      .caption { font-size: 12.5px; line-height: 1.4; color: #5a5a55; }
      .mark { width: 32px; height: 32px; border-radius: 9px; background: #000; }
      .dot { position: absolute; width: 8px; height: 8px; border-radius: 4px; }
      .brand { font-size: 16px; font-weight: 500; letter-spacing: -.01em; }
      .inst { text-align: right; font-size: 13px; color: #5a5a55; }
      .chip { display: inline-block; height: 22px; line-height: 22px; padding: 0 10px; border-radius: 11px; background: #ebebf0;
        font-size: 11px; font-weight: 500; color: #000; vertical-align: middle; }
      .chip--ink { background: #000; color: #fff; }
      .chip i { display: inline-block; width: 8px; height: 8px; border-radius: 4px; margin-right: 6px; vertical-align: 0; }
      .tile { border-radius: 24px; text-align: center; }
      .link { font-family: 'Geist Mono'; font-size: 11px; color: #4a3df5; }
      .qr { width: 62px; height: 62px; padding: 7px; background: #fff; border-radius: 12px; border: 1px solid #ececf2; }
      .foot { border-top: 1px solid #e6e6e3; padding-top: 10px; }
      .card { background: #fff; border-radius: 20px; border: 1px solid #ececf2; }
      .date { font-size: 17px; font-weight: 500; letter-spacing: -.01em; margin-top: 5px; }
      """;

  private final String verifyBase;

  public CertificatePdfWriter(@Value("${studymatch.frontend-url}") String frontendUrl) {
    this.verifyBase = StringUtils.trimTrailingCharacter(frontendUrl, '/') + "/verify/";
  }

  // one achievement, A4 landscape
  public byte[] writeCertificate(Holder holder, Entry entry, String code, LocalDate issuedAt, Locale locale) {
    Texts t = new Texts(locale);
    Art art = ART.get(entry.achievement());
    String url = verifyBase + code;
    StringBuilder body = new StringBuilder();
    body.append(header(t, 15, 15, 297));
    body.append("<div class=\"abs\" style=\"left:15mm;top:44mm;width:178mm\">")
        .append("<p style=\"font-size:17px;font-weight:500;color:#4a3df5\">").append(esc(t.get("certTitle")))
        .append(" <span style=\"display:inline-block;width:40px;height:1px;background:#a5a0f8;vertical-align:middle;margin-left:8px\"></span></p>")
        .append("<p style=\"font-size:15px;color:#5a5a55;margin-top:6mm\">").append(esc(t.get("certifies"))).append("</p>")
        .append("<p style=\"font-size:56px;line-height:1;letter-spacing:-.035em;margin:2mm 0 2.5mm\">").append(esc(holder.name()))
        .append(",</p>")
        .append("<p style=\"font-size:15px;line-height:1.45;color:#5a5a55\">").append(who(t, holder)).append(", ")
        .append(esc(t.get("hasEarned"))).append("</p>")
        .append("<p style=\"margin:5mm 0 2.5mm\"><span style=\"font-size:38px;line-height:1;letter-spacing:-.025em;vertical-align:middle\">")
        .append(esc(t.name(entry.achievement()))).append("</span> &#160; ").append(rarityChip(t, art.rarity())).append("</p>")
        .append("<table style=\"border-collapse:collapse\"><tr><td style=\"width:22px;vertical-align:top;padding-top:2px\">")
        .append(SPARKLE).append("</td><td style=\"font-size:15px;line-height:1.35\">")
        .append(esc(t.criterion(entry.achievement()))).append("</td></tr></table>")
        .append(evidence(t, entry.evidence()))
        .append("</div>");
    body.append("<div class=\"abs\" style=\"right:15mm;top:50mm;width:74mm\">")
        .append(tile(entry.achievement(), art, 74, 8))
        .append("<p class=\"eyebrow\" style=\"text-align:center;margin-top:12px\">").append(esc(t.get("category"))).append("</p></div>");
    body.append("<div class=\"abs\" style=\"left:15mm;top:166mm\"><p class=\"eyebrow\">").append(esc(t.get("earned")))
        .append("</p><p class=\"date\">").append(t.date(entry.earnedAt())).append("</p></div>")
        .append("<div class=\"abs\" style=\"left:75mm;top:166mm\"><p class=\"eyebrow\">").append(esc(t.get("issued")))
        .append("</p><p class=\"date\">").append(t.date(issuedAt)).append("</p></div>");
    body.append(verify(t, url, 15, 160, 297));
    body.append(foot(t, url, "certId", code, 15, 190, 297));
    return render(page("A4 landscape", 297, 210, body), locale);
  }

  // several achievements on one A4 portrait page
  public byte[] writeRecord(Holder holder, List<Entry> entries, String code, LocalDate issuedAt, Locale locale) {
    Texts t = new Texts(locale);
    String url = verifyBase + code;
    StringBuilder body = new StringBuilder();
    body.append(header(t, 13, 13, 210));
    body.append("<div class=\"abs\" style=\"left:13mm;top:34mm;width:184mm\">")
        .append("<p style=\"font-size:40px;line-height:1;letter-spacing:-.025em\">").append(esc(t.get("recordTitle"))).append("</p>")
        .append("<p style=\"font-size:14px;line-height:1.45;color:#5a5a55;margin-top:4mm;width:150mm\">")
        .append(esc(t.get("certifies"))).append(" <b style=\"font-weight:500;color:#000\">").append(esc(holder.name()))
        .append("</b>, ").append(who(t, holder)).append(", ").append(esc(t.get("hasEarnedAll"))).append("</p>")
        .append("<p style=\"margin-top:5mm\"><span style=\"font-size:14px;font-weight:500;vertical-align:middle\">")
        .append(esc(t.format("count", entries.size(), Achievement.values().length))).append("</span></p>")
        .append("<table style=\"width:184mm;border-collapse:separate;border-spacing:0 10px;margin-top:3mm\">");
    for (int i = 0; i < entries.size(); i += 2) {
      body.append("<tr><td style=\"width:89mm;padding-right:6mm\">").append(card(t, entries.get(i))).append("</td><td style=\"width:89mm\">");
      if (i + 1 < entries.size()) {
        body.append(card(t, entries.get(i + 1)));
      }
      body.append("</td></tr>");
    }
    body.append("</table></div>");
    body.append("<div class=\"abs\" style=\"left:13mm;top:250mm;width:95mm\"><p class=\"eyebrow\">").append(esc(t.get("issued")))
        .append("</p><p class=\"date\" style=\"font-size:15px\">").append(t.date(issuedAt)).append("</p>")
        .append("<p class=\"caption\" style=\"margin-top:8px\">").append(esc(t.get("awardedBy"))).append("</p></div>");
    body.append(verify(t, url, 13, 248, 210));
    body.append(foot(t, url, "recordId", code, 13, 279, 210));
    return render(page("A4 portrait", 210, 297, body), locale);
  }

  private static String header(Texts t, int side, int top, int width) {
    return "<div class=\"abs\" style=\"left:" + side + "mm;top:" + top + "mm\"><div class=\"mark\" style=\"position:relative\">"
        + "<span class=\"dot\" style=\"left:7px;top:7px;background:#7a70ff\"></span>"
        + "<span class=\"dot\" style=\"left:17px;top:7px;background:#2a9fd6\"></span>"
        + "<span class=\"dot\" style=\"left:7px;top:17px;background:#eb6834\"></span>"
        + "<span class=\"dot\" style=\"left:17px;top:17px;background:#1baf7a\"></span></div></div>"
        + "<p class=\"abs brand\" style=\"left:" + (side + 11) + "mm;top:" + (top + 2) + "mm\">StudyMatch</p>"
        + "<p class=\"abs inst\" style=\"right:" + side + "mm;top:" + (top + 1) + "mm;width:" + (width / 2) + "mm\">"
        + esc(t.get("institution")) + " &#160; <span class=\"chip chip--ink\">" + esc(t.get("institutionShort")) + "</span></p>";
  }

  private static String who(Texts t, Holder holder) {
    String line = esc(t.get("studentNo")) + " <b style=\"font-weight:500;color:#000\">" + holder.number() + "</b>";
    return holder.degree() == null ? line : line + ", <b style=\"font-weight:500;color:#000\">" + esc(holder.degree()) + "</b>";
  }

  private static String rarityChip(Texts t, String rarity) {
    return "<span class=\"chip\"><i style=\"background:" + RARITY_COLOURS.get(rarity) + "\"></i>"
        + esc(t.get("rarity." + rarity)) + "</span>";
  }

  // the achievement's art on its colour, size in mm
  private static String tile(Achievement achievement, Art art, int size, int border) {
    int inner = size - 2 * border / 4;
    String svg = readResource("certificates/art/" + achievement.name() + ".svg")
        .replaceFirst("<svg ", "<svg style=\"width:" + (inner * 0.72) + "mm;height:" + (inner * 0.72) + "mm\" ");
    return "<div class=\"tile\" style=\"width:" + size + "mm;height:" + size + "mm;border:" + border + "px solid #fff;"
        + "background-color:" + art.to() + ";background-image:linear-gradient(160deg," + art.from() + "," + art.to() + ");"
        + "padding-top:" + (inner * 0.12) + "mm;box-sizing:border-box\">" + svg + "</div>";
  }

  private static String evidence(Texts t, CertificateEvidence evidence) {
    if (evidence == null) {
      return "";
    }
    StringBuilder stats = new StringBuilder();
    stats.append(stat(String.valueOf(evidence.roomsHeld()), "", t.get("roomsHeld"), true))
        .append(stat(String.valueOf(evidence.studentsTutored()), "", t.get("studentsTutored"), true));
    if (evidence.averageRating() != null) {
      stats.append(stat(t.decimal(evidence.averageRating()), "/5", t.get("averageRating"), evidence.subject() != null));
    }
    if (evidence.subject() != null) {
      stats.append("<td style=\"padding-left:14px;vertical-align:top\"><p style=\"font-size:16px;font-weight:500;line-height:1.15;"
          + "margin:4px 0 6px\">" + esc(evidence.subject()) + "</p><p class=\"caption\">" + esc(t.get("subject")) + "</p></td>");
    }
    return "<div class=\"card\" style=\"margin-top:6mm;padding:12px 14px;width:165mm\"><p class=\"eyebrow\">" + esc(t.get("evidence"))
        + "</p><table style=\"margin-top:8px;border-collapse:collapse\"><tr>" + stats + "</tr></table>"
        + "<p class=\"caption\" style=\"margin-top:8px;font-size:11px;color:#8a8a84\">"
        + esc(t.format("recorded", t.date(evidence.recordedAt()))) + "</p></div>";
  }

  private static String stat(String value, String suffix, String label, boolean line) {
    return "<td style=\"padding:0 14px;vertical-align:top;" + (line ? "border-right:1px solid #e6e6e3;" : "") + "\">"
        + "<p style=\"font-size:32px;line-height:1;letter-spacing:-.04em;margin-bottom:4px\">" + esc(value)
        + "<span style=\"font-size:15px;color:#8a8a84;letter-spacing:0\">" + esc(suffix) + "</span></p>"
        + "<p class=\"caption\" style=\"white-space:nowrap\">" + esc(label) + "</p></td>";
  }

  private static String card(Texts t, Entry entry) {
    Art art = ART.get(entry.achievement());
    // fixed height, so two cards side by side line up even when a name wraps
    return "<div class=\"card\" style=\"padding:8px;height:36mm;box-sizing:border-box\">"
        + "<table style=\"border-collapse:collapse;width:100%;height:100%\"><tr>"
        + "<td style=\"width:23mm;vertical-align:middle\">" + tile(entry.achievement(), art, 23, 0) + "</td>"
        + "<td style=\"padding-left:12px;vertical-align:middle\">"
        + "<p><span style=\"font-size:15px;font-weight:500;letter-spacing:-.015em\">" + esc(t.name(entry.achievement()))
        + "</span> &#160; " + rarityChip(t, art.rarity()) + "</p>"
        + "<p style=\"font-size:12px;line-height:1.35;color:#5a5a55;margin:3px 0 5px\">" + esc(t.criterion(entry.achievement())) + "</p>"
        + "<p><span class=\"eyebrow\">" + esc(t.get("category")) + "</span> &#160; "
        + "<span style=\"font-size:11px;font-weight:500;color:#007a3b\">" + t.shortDate(entry.earnedAt()) + "</span></p>"
        + "</td></tr></table></div>";
  }

  // the QR and the link written out, so it also works on paper
  private static String verify(Texts t, String url, int side, int top, int width) {
    String shown = url.replaceFirst("^https?://", "");
    int code = shown.lastIndexOf('/') + 1;
    return "<div class=\"abs\" style=\"right:" + side + "mm;top:" + top + "mm;width:" + (width / 2) + "mm;text-align:right\">"
        + "<table style=\"border-collapse:collapse;margin-left:auto\"><tr><td style=\"text-align:right;vertical-align:middle;"
        + "padding-right:14px\"><p class=\"caption\">" + esc(t.get("scan")) + "</p><p class=\"link\">" + esc(shown.substring(0, code))
        + "<br />" + esc(shown.substring(code)) + "</p></td>"
        + "<td><div class=\"qr\">" + qr(url) + "</div></td></tr></table></div>";
  }

  private static String foot(Texts t, String url, String idLabel, String code, int side, int top, int width) {
    return "<div class=\"abs foot\" style=\"left:" + side + "mm;top:" + top + "mm;width:" + (width - 2 * side) + "mm\">"
        + "<table style=\"width:100%;border-collapse:collapse\"><tr>"
        + "<td class=\"eyebrow\">" + esc(t.get("verifyAt")) + " <b>" + esc(url.replaceFirst("^https?://", "")) + "</b></td>"
        + "<td class=\"eyebrow\" style=\"text-align:right\">" + esc(t.get(idLabel)) + " <b>" + esc(shortId(code)) + "</b></td>"
        + "</tr></table></div>";
  }

  // the start of the code, short enough to read out, the link keeps all of it
  private static String shortId(String code) {
    return code.length() > 8 ? code.substring(0, 8) : code;
  }

  // one square per dark module, drawn as a single path
  private static String qr(String url) {
    try {
      BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 0, 0,
          Map.of(EncodeHintType.MARGIN, 0, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
      StringBuilder path = new StringBuilder();
      for (int y = 0; y < matrix.getHeight(); y++) {
        for (int x = 0; x < matrix.getWidth(); x++) {
          if (matrix.get(x, y)) {
            path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
          }
        }
      }
      return "<svg xmlns=\"http://www.w3.org/2000/svg\" style=\"width:62px;height:62px\" viewBox=\"0 0 " + matrix.getWidth()
          + " " + matrix.getHeight() + "\" shape-rendering=\"crispEdges\"><path fill=\"#0d0b24\" d=\"" + path + "\"/></svg>";
    } catch (WriterException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String page(String size, int width, int height, StringBuilder body) {
    return "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"utf-8\" /><style>" + CSS
        + "@page { size: " + size + "; margin: 0; }</style></head><body><div class=\"page\" style=\"width:" + width + "mm;height:"
        + height + "mm\">" + body + "</div></body></html>";
  }

  private byte[] render(String html, Locale locale) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PdfRendererBuilder builder = new PdfRendererBuilder();
      builder.useFastMode();
      builder.useSVGDrawer(new BatikSVGDrawer());
      builder.useFont(() -> stream("certificates/fonts/Geist-Regular.ttf"), "Geist", 400, FontStyle.NORMAL, true);
      builder.useFont(() -> stream("certificates/fonts/Geist-Medium.ttf"), "Geist", 500, FontStyle.NORMAL, true);
      builder.useFont(() -> stream("certificates/fonts/GeistMono-Regular.ttf"), "Geist Mono", 400, FontStyle.NORMAL, true);
      builder.withHtmlContent(html, null);
      builder.useDefaultPageSize(210, 297, PdfRendererBuilder.PageSizeUnits.MM);
      builder.withProducer("StudyMatch");
      builder.toStream(out);
      builder.run();
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static InputStream stream(String path) {
    return CertificatePdfWriter.class.getClassLoader().getResourceAsStream(path);
  }

  private static String readResource(String path) {
    try (InputStream in = stream(path)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String esc(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  // the texts and the date and number formats of one language
  private static final class Texts {

    private final ResourceBundle bundle;
    private final Locale locale;

    Texts(Locale locale) {
      this.locale = locale;
      this.bundle = ResourceBundle.getBundle("certificates.messages", locale, ResourceBundle.Control
          .getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    String get(String key) {
      return bundle.getString(key);
    }

    String format(String key, Object... values) {
      return new MessageFormat(get(key), locale).format(values);
    }

    String name(Achievement achievement) {
      return get(achievement.name() + ".name");
    }

    String criterion(Achievement achievement) {
      return get(achievement.name() + ".criterion");
    }

    String date(LocalDate date) {
      return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale));
    }

    String shortDate(LocalDate date) {
      return date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale));
    }

    String decimal(double value) {
      NumberFormat format = NumberFormat.getNumberInstance(locale);
      format.setMaximumFractionDigits(1);
      format.setMinimumFractionDigits(1);
      return format.format(value);
    }

  }

}
