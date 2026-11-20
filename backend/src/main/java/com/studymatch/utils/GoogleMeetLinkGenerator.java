package com.studymatch.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

// a Google Calendar event with a Meet conference, on the one StudyMatch Google account set up on the server
@Component
public class GoogleMeetLinkGenerator implements MeetLinkGenerator {

  private static final Logger log = LoggerFactory.getLogger(GoogleMeetLinkGenerator.class);

  static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
  static final String EVENTS_URL = "https://www.googleapis.com/calendar/v3/calendars/primary/events?conferenceDataVersion=1";
  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
  private static final Pattern MEET_LINK = Pattern.compile("^https://meet\\.google\\.com/[a-z]{3}-[a-z]{4}-[a-z]{3}$");

  private final RestClient restClient;
  private final String clientId;
  private final String clientSecret;
  private final String refreshToken;
  private final String timeZone;

  @Autowired
  public GoogleMeetLinkGenerator(@Value("${studymatch.google.client-id}") String clientId,
      @Value("${studymatch.google.client-secret}") String clientSecret,
      @Value("${studymatch.google.refresh-token}") String refreshToken,
      @Value("${studymatch.google.time-zone}") String timeZone) {
    this(RestClient.builder(), clientId, clientSecret, refreshToken, timeZone);
  }

  // the tests hand in a builder bound to a fake server
  GoogleMeetLinkGenerator(RestClient.Builder builder, String clientId, String clientSecret, String refreshToken,
      String timeZone) {
    this.restClient = builder.build();
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.refreshToken = refreshToken;
    this.timeZone = timeZone;
  }

  @Override
  public String createLink(String title, LocalDateTime startsAt, LocalDateTime endsAt) {
    if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
      log.warn("Google isn't set up (GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_REFRESH_TOKEN), no Meet link");
      throw new StudyMatchException(ErrorCode.MEET_LINK_UNAVAILABLE);
    }
    try {
      Map<?, ?> event = createEvent(accessToken(), title, startsAt, endsAt);
      Object link = event == null ? null : event.get("hangoutLink");
      if (link instanceof String meetLink && MEET_LINK.matcher(meetLink).matches()) {
        return meetLink;
      }
      log.warn("Google created the event but gave no Meet link");
    } catch (RestClientException e) {
      log.warn("Google couldn't create the Meet link", e);
    }
    throw new StudyMatchException(ErrorCode.MEET_LINK_UNAVAILABLE);
  }

  // the refresh token never expires, it gives a new access token each time
  private String accessToken() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    form.add("refresh_token", refreshToken);
    form.add("grant_type", "refresh_token");
    Map<?, ?> token = restClient.post().uri(TOKEN_URL)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .body(Map.class);
    if (token == null || !(token.get("access_token") instanceof String value)) {
      throw new RestClientException("Google gave no access token");
    }
    return value;
  }

  private Map<?, ?> createEvent(String accessToken, String title, LocalDateTime startsAt, LocalDateTime endsAt) {
    Map<String, Object> event = Map.of(
        "summary", title,
        "start", Map.of("dateTime", startsAt.format(DATE_TIME), "timeZone", timeZone),
        "end", Map.of("dateTime", endsAt.format(DATE_TIME), "timeZone", timeZone),
        "conferenceData", Map.of("createRequest", Map.of(
            "requestId", UUID.randomUUID().toString(),
            "conferenceSolutionKey", Map.of("type", "hangoutsMeet"))));
    return restClient.post().uri(EVENTS_URL)
        .headers(headers -> headers.setBearerAuth(accessToken))
        .contentType(MediaType.APPLICATION_JSON)
        .body(event)
        .retrieve()
        .body(Map.class);
  }

}
