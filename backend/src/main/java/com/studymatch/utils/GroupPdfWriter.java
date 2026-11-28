package com.studymatch.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

// export groups: the same rows as the CSV, one table per group, in the certificates' fonts
@Component
public class GroupPdfWriter {

  private static final String CSS = """
      @page { size: A4 portrait; margin: 15mm; }
      * { margin: 0; padding: 0; }
      body { font-family: 'Geist'; font-size: 11px; color: #000; }
      .brand { font-size: 13px; font-weight: 500; color: #5a5a55; }
      h1 { font-size: 26px; font-weight: 500; letter-spacing: -.02em; margin-top: 4mm; }
      .subtitle { font-size: 13px; color: #5a5a55; margin-top: 1.5mm; }
      .group { margin-top: 7mm; page-break-inside: avoid; }
      h2 { font-size: 15px; font-weight: 500; }
      h2 span { font-size: 11px; font-weight: 400; color: #5a5a55; margin-left: 6px; }
      table { width: 100%; border-collapse: collapse; margin-top: 2mm; }
      th { font-family: 'Geist Mono'; font-size: 9.5px; font-weight: 400; text-transform: uppercase; color: #5a5a55;
        text-align: left; padding: 4px 6px; border-bottom: 1px solid #000; }
      td { padding: 5px 6px; border-bottom: 1px solid #e6e6e3; }
      .number { width: 28mm; font-family: 'Geist Mono'; }
      .email { width: 70mm; font-family: 'Geist Mono'; }
      """;

  // the rows come sorted by group, as they go in the CSV
  public byte[] write(String subject, String assignment, List<GroupCsvWriter.Row> rows, Locale locale) {
    ResourceBundle texts = ResourceBundle.getBundle("groups.messages", locale, ResourceBundle.Control
        .getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    Map<Integer, List<GroupCsvWriter.Row>> groups = rows.stream().collect(Collectors.groupingBy(
        GroupCsvWriter.Row::groupNumber, LinkedHashMap::new, Collectors.toList()));

    StringBuilder body = new StringBuilder();
    body.append("<p class=\"brand\">StudyMatch</p>")
        .append("<h1>").append(esc(texts.getString("title"))).append("</h1>")
        .append("<p class=\"subtitle\">").append(esc(subject)).append(" &#183; ").append(esc(assignment)).append("</p>");
    // the group number as text, so group 1000 isn't printed as 1,000
    groups.forEach((number, members) -> {
      body.append("<div class=\"group\"><h2>").append(esc(format(texts, locale, "group", String.valueOf(number))))
          .append("<span>").append(esc(format(texts, locale, "members", members.size()))).append("</span></h2>")
          .append("<table><tr><th class=\"number\">").append(esc(texts.getString("studentNumber"))).append("</th><th>")
          .append(esc(texts.getString("name"))).append("</th><th class=\"email\">")
          .append(esc(texts.getString("institutionalEmail"))).append("</th></tr>");
      for (GroupCsvWriter.Row member : members) {
        body.append("<tr><td class=\"number\">").append(member.studentNumber()).append("</td><td>")
            .append(esc(member.name())).append("</td><td class=\"email\">")
            .append(esc(member.institutionalEmail() == null ? "" : member.institutionalEmail())).append("</td></tr>");
      }
      body.append("</table></div>");
    });
    return render("<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"utf-8\" /><style>"
        + CSS + "</style></head><body>" + body + "</body></html>");
  }

  private static String format(ResourceBundle texts, Locale locale, String key, Object value) {
    return new MessageFormat(texts.getString(key), locale).format(new Object[] { value });
  }

  private static byte[] render(String html) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PdfRendererBuilder builder = new PdfRendererBuilder();
      builder.useFastMode();
      builder.useFont(() -> stream("certificates/fonts/Geist-Regular.ttf"), "Geist", 400, FontStyle.NORMAL, true);
      builder.useFont(() -> stream("certificates/fonts/Geist-Medium.ttf"), "Geist", 500, FontStyle.NORMAL, true);
      builder.useFont(() -> stream("certificates/fonts/GeistMono-Regular.ttf"), "Geist Mono", 400, FontStyle.NORMAL, true);
      builder.withHtmlContent(html, null);
      builder.withProducer("StudyMatch");
      builder.toStream(out);
      builder.run();
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static InputStream stream(String path) {
    return GroupPdfWriter.class.getClassLoader().getResourceAsStream(path);
  }

  private static String esc(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

}
