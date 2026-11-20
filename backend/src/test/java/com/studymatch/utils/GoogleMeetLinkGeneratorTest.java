package com.studymatch.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class GoogleMeetLinkGeneratorTest {

  private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 12, 3, 14, 0);
  private static final LocalDateTime ENDS_AT = LocalDateTime.of(2026, 12, 3, 16, 0);
  private static final String TOKEN = "{\"access_token\":\"test-access-token\",\"expires_in\":3599}";

  private RestClient.Builder builder;
  private MockRestServiceServer google;

  // a fake Google answering on the real addresses
  @BeforeEach
  void setUp() {
    builder = RestClient.builder();
    google = MockRestServiceServer.bindTo(builder).build();
  }

  private GoogleMeetLinkGenerator generator(String clientId) {
    return new GoogleMeetLinkGenerator(builder, clientId, "test-secret", "test-refresh-token", "Europe/Lisbon");
  }

  private void tokenIsGiven() {
    google.expect(requestTo(GoogleMeetLinkGenerator.TOKEN_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().formDataContains(Map.of("grant_type", "refresh_token",
            "refresh_token", "test-refresh-token", "client_id", "test-client")))
        .andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
  }

  @Test
  @DisplayName("Creates a calendar event with a Meet conference and returns its link")
  void createsLink() {
    // Arrange
    GoogleMeetLinkGenerator generator = generator("test-client");
    tokenIsGiven();
    google.expect(requestTo(GoogleMeetLinkGenerator.EVENTS_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer test-access-token"))
        .andExpect(jsonPath("$.summary").value("Test Maths - StudyRoom 2026-12-03"))
        .andExpect(jsonPath("$.start.dateTime").value("2026-12-03T14:00:00"))
        .andExpect(jsonPath("$.end.dateTime").value("2026-12-03T16:00:00"))
        .andExpect(jsonPath("$.start.timeZone").value("Europe/Lisbon"))
        .andExpect(jsonPath("$.conferenceData.createRequest.conferenceSolutionKey.type").value("hangoutsMeet"))
        .andRespond(withSuccess("{\"id\":\"test-event\",\"hangoutLink\":\"https://meet.google.com/xyz-wxyz-xyz\"}",
            MediaType.APPLICATION_JSON));

    // Act
    String link = generator.createLink("Test Maths - StudyRoom 2026-12-03", STARTS_AT, ENDS_AT);

    // Assert
    assertEquals("https://meet.google.com/xyz-wxyz-xyz", link);
    google.verify();
  }

  @Test
  @DisplayName("Not set up on the server gives MEET_LINK_UNAVAILABLE without calling Google")
  void notSetUp() {
    // Arrange
    GoogleMeetLinkGenerator generator = generator("");

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> generator.createLink("Test Maths - StudyRoom 2026-12-03", STARTS_AT, ENDS_AT));

    // Assert
    assertEquals(ErrorCode.MEET_LINK_UNAVAILABLE, e.getCode());
    google.verify();
  }

  @Test
  @DisplayName("A refused refresh token gives MEET_LINK_UNAVAILABLE")
  void tokenRefused() {
    // Arrange
    GoogleMeetLinkGenerator generator = generator("test-client");
    google.expect(requestTo(GoogleMeetLinkGenerator.TOKEN_URL))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":\"invalid_grant\"}")
            .contentType(MediaType.APPLICATION_JSON));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> generator.createLink("Test Maths - StudyRoom 2026-12-03", STARTS_AT, ENDS_AT));

    // Assert
    assertEquals(ErrorCode.MEET_LINK_UNAVAILABLE, e.getCode());
  }

  @Test
  @DisplayName("An event created without a Meet link gives MEET_LINK_UNAVAILABLE")
  void eventWithoutLink() {
    // Arrange
    GoogleMeetLinkGenerator generator = generator("test-client");
    tokenIsGiven();
    google.expect(requestTo(GoogleMeetLinkGenerator.EVENTS_URL))
        .andRespond(withSuccess("{\"id\":\"test-event\"}", MediaType.APPLICATION_JSON));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> generator.createLink("Test Maths - StudyRoom 2026-12-03", STARTS_AT, ENDS_AT));

    // Assert
    assertEquals(ErrorCode.MEET_LINK_UNAVAILABLE, e.getCode());
  }

  @Test
  @DisplayName("Google failing to create the event gives MEET_LINK_UNAVAILABLE")
  void eventRefused() {
    // Arrange
    GoogleMeetLinkGenerator generator = generator("test-client");
    tokenIsGiven();
    google.expect(requestTo(GoogleMeetLinkGenerator.EVENTS_URL))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> generator.createLink("Test Maths - StudyRoom 2026-12-03", STARTS_AT, ENDS_AT));

    // Assert
    assertEquals(ErrorCode.MEET_LINK_UNAVAILABLE, e.getCode());
  }

}
