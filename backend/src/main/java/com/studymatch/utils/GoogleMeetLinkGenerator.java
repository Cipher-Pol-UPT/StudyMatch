package com.studymatch.utils;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

// an open Meet space on the one StudyMatch Google account set up on the server. Open, so students join
// without knocking: nobody from that account is there to let them in
@Component
public class GoogleMeetLinkGenerator implements MeetLinkGenerator {

  private static final Logger log = LoggerFactory.getLogger(GoogleMeetLinkGenerator.class);

  static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
  static final String SPACES_URL = "https://meet.googleapis.com/v2/spaces";
  // it runs inside the create transaction, so a Google that hangs can't hold the request and its connection
  static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final Pattern MEET_LINK = Pattern.compile("^https://meet\\.google\\.com/[a-z]{3}-[a-z]{4}-[a-z]{3}$");

  private final RestClient restClient;
  private final String clientId;
  private final String clientSecret;
  private final String refreshToken;

  @Autowired
  public GoogleMeetLinkGenerator(@Value("${studymatch.google.client-id}") String clientId,
      @Value("${studymatch.google.client-secret}") String clientSecret,
      @Value("${studymatch.google.refresh-token}") String refreshToken) {
    this(RestClient.builder().requestFactory(withTimeouts()), clientId, clientSecret, refreshToken);
  }

  // the tests hand in a builder bound to a fake server
  GoogleMeetLinkGenerator(RestClient.Builder builder, String clientId, String clientSecret, String refreshToken) {
    this.restClient = builder.build();
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.refreshToken = refreshToken;
  }

  static SimpleClientHttpRequestFactory withTimeouts() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(TIMEOUT);
    factory.setReadTimeout(TIMEOUT);
    return factory;
  }

  @Override
  public String createLink() {
    if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
      log.warn("Google isn't set up (GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_REFRESH_TOKEN), no Meet link");
      throw new StudyMatchException(ErrorCode.MEET_LINK_UNAVAILABLE);
    }
    try {
      Map<?, ?> space = createSpace(accessToken());
      Object link = space == null ? null : space.get("meetingUri");
      if (link instanceof String meetLink && MEET_LINK.matcher(meetLink).matches()) {
        return meetLink;
      }
      log.warn("Google created the Meet space but gave no link");
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

  private Map<?, ?> createSpace(String accessToken) {
    return restClient.post().uri(SPACES_URL)
        .headers(headers -> headers.setBearerAuth(accessToken))
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("config", Map.of("accessType", "OPEN")))
        .retrieve()
        .body(Map.class);
  }

}
