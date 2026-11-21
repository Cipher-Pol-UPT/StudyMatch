package com.studymatch.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class GoogleMeetLinkGeneratorTest {

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
    return new GoogleMeetLinkGenerator(builder, clientId, "test-secret", "test-refresh-token");
  }

  private void tokenIsGiven() {
    google.expect(requestTo(GoogleMeetLinkGenerator.TOKEN_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().formDataContains(Map.of("grant_type", "refresh_token",
            "refresh_token", "test-refresh-token", "client_id", "test-client")))
        .andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
  }

  private void assertUnavailable(GoogleMeetLinkGenerator generator) {
    StudyMatchException e = assertThrows(StudyMatchException.class, generator::createLink);
    assertEquals(ErrorCode.MEET_LINK_UNAVAILABLE, e.getCode());
  }

  @Test
  @DisplayName("Creates an open Meet space, so students join without knocking, and returns its link")
  void createsLink() {
    // Arrange
    GoogleMeetLinkGenerator generator = generator("test-client");
    tokenIsGiven();
    google.expect(requestTo(GoogleMeetLinkGenerator.SPACES_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer test-access-token"))
        .andExpect(jsonPath("$.config.accessType").value("OPEN"))
        .andRespond(withSuccess("{\"name\":\"spaces/test\",\"meetingUri\":\"https://meet.google.com/xyz-wxyz-xyz\","
            + "\"meetingCode\":\"xyz-wxyz-xyz\"}", MediaType.APPLICATION_JSON));

    // Act
    String link = generator.createLink();

    // Assert
    assertEquals("https://meet.google.com/xyz-wxyz-xyz", link);
    google.verify();
  }

  @Test
  @DisplayName("Not set up on the server gives MEET_LINK_UNAVAILABLE without calling Google")
  void notSetUp() {
    // Act + Assert
    assertUnavailable(generator(""));
    google.verify();
  }

  @Test
  @DisplayName("A refused refresh token gives MEET_LINK_UNAVAILABLE")
  void tokenRefused() {
    // Arrange
    google.expect(requestTo(GoogleMeetLinkGenerator.TOKEN_URL))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":\"invalid_grant\"}")
            .contentType(MediaType.APPLICATION_JSON));

    // Act + Assert
    assertUnavailable(generator("test-client"));
  }

  @Test
  @DisplayName("A space created without a link gives MEET_LINK_UNAVAILABLE")
  void spaceWithoutLink() {
    // Arrange
    tokenIsGiven();
    google.expect(requestTo(GoogleMeetLinkGenerator.SPACES_URL))
        .andRespond(withSuccess("{\"name\":\"spaces/test\"}", MediaType.APPLICATION_JSON));

    // Act + Assert
    assertUnavailable(generator("test-client"));
  }

  @Test
  @DisplayName("Google failing to create the space gives MEET_LINK_UNAVAILABLE")
  void spaceRefused() {
    // Arrange
    tokenIsGiven();
    google.expect(requestTo(GoogleMeetLinkGenerator.SPACES_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    // Act + Assert
    assertUnavailable(generator("test-client"));
  }

  @Test
  @DisplayName("Google not answering in time gives MEET_LINK_UNAVAILABLE instead of hanging")
  void googleTimesOut() {
    // Arrange
    tokenIsGiven();
    google.expect(requestTo(GoogleMeetLinkGenerator.SPACES_URL)).andRespond(withException(new SocketTimeoutException()));

    // Act + Assert
    assertUnavailable(generator("test-client"));
  }

  @Test
  @DisplayName("The real client gives up after 5 seconds to connect and 5 to read")
  void realClientHasTimeouts() {
    // Act
    SimpleClientHttpRequestFactory factory = GoogleMeetLinkGenerator.withTimeouts();

    // Assert - without them a Google that hangs would hold the create request and its database connection
    assertEquals(5000, ReflectionTestUtils.getField(factory, "connectTimeout"));
    assertEquals(5000, ReflectionTestUtils.getField(factory, "readTimeout"));
  }

}
