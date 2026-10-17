package com.studymatch.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import com.studymatch.exceptions.ErrorCode;

import tools.jackson.databind.json.JsonMapper;

class SecurityErrorWriterTest {

  private SecurityErrorWriter errorWriter;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    // real json mapper (nothing slow or external about it), fake http response
    errorWriter = new SecurityErrorWriter(JsonMapper.builder().build());
    response = new MockHttpServletResponse();
  }

  @Test
  @DisplayName("Writes the status, json content type and the code as the body")
  void writesErrorResponse() throws Exception {
    // Act
    errorWriter.write(response, ErrorCode.ACCOUNT_BLOCKED);

    // Assert
    assertEquals(403, response.getStatus());
    assertEquals("application/json", response.getContentType());
    assertEquals("{\"code\":\"ACCOUNT_BLOCKED\"}", response.getContentAsString());
  }

  @Test
  @DisplayName("The status comes from the error code")
  void statusComesFromCode() throws Exception {
    // Act
    errorWriter.write(response, ErrorCode.UNAUTHENTICATED);

    // Assert
    assertEquals(401, response.getStatus());
  }

}
