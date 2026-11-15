package com.studymatch.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;

class OpenApiConfigTest {

  private final OpenApiConfig config = new OpenApiConfig();

  // fake controllers, one method per case

  @RequestMapping("/users/")
  static class FakeUserController {

    @PostMapping("login")
    public void login(@RequestBody String body) {
    }

    @PostMapping("logins")
    public void notPublic() {
    }

  }

  // no path on the class, the method has all of it
  static class FakeRootController {

    @PostMapping("/users/login")
    public void login() {
    }

  }

  @RequestMapping("/things/")
  static class FakeThingController {

    @GetMapping
    public void list() {
    }

    @GetMapping("search")
    public void search(@RequestParam String text) {
    }

    @PutMapping("{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @ErrorCodes({ ErrorCode.STUDENT_NOT_ENROLLED, ErrorCode.NOT_FOUND, ErrorCode.ASSIGNMENT_ALREADY_EXISTS,
        ErrorCode.ACCESS_DENIED })
    public void update(@PathVariable Long id, @RequestBody String body) {
    }

  }

  // status -> description, sorted so the order is easy to compare
  private Map<String, String> responsesOf(Object controller, String methodName, Class<?>... parameterTypes)
      throws NoSuchMethodException {
    HandlerMethod handler = new HandlerMethod(controller, controller.getClass().getMethod(methodName, parameterTypes));
    Operation operation = config.errorCodesCustomizer().customize(new Operation(), handler);

    Map<String, String> responses = new TreeMap<>();
    operation.getResponses().forEach((status, response) -> responses.put(status, response.getDescription()));
    return responses;
  }

  @Test
  @DisplayName("A GET that needs a login and takes nothing only gets UNAUTHENTICATED")
  void plainGetOnlyNeedsLogin() throws Exception {
    // Act
    Map<String, String> responses = responsesOf(new FakeThingController(), "list");

    // Assert
    assertEquals(Map.of("401", "`UNAUTHENTICATED`"), responses);
  }

  @Test
  @DisplayName("A ?param counts as input, so it can get VALIDATION_FAILED")
  void requestParamIsInput() throws Exception {
    // Act
    Map<String, String> responses = responsesOf(new FakeThingController(), "search", String.class);

    // Assert
    assertEquals("`VALIDATION_FAILED`", responses.get("400"));
  }

  @Test
  @DisplayName("A public endpoint (login) has no UNAUTHENTICATED, but still needs the CSRF token")
  void publicEndpointHasNoLoginError() throws Exception {
    // Act
    Map<String, String> responses = responsesOf(new FakeUserController(), "login", String.class);

    // Assert
    assertFalse(responses.containsKey("401"));
    assertEquals("`CSRF_TOKEN_INVALID`", responses.get("403"));
  }

  @Test
  @DisplayName("Only the exact public path counts: /users/logins needs a login")
  void similarPathIsNotPublic() throws Exception {
    // Act
    Map<String, String> responses = responsesOf(new FakeUserController(), "notPublic");

    // Assert
    assertEquals("`UNAUTHENTICATED`", responses.get("401"));
  }

  @Test
  @DisplayName("Without a path on the class, the method's path alone is matched")
  void controllerWithoutClassPath() throws Exception {
    // Act
    Map<String, String> responses = responsesOf(new FakeRootController(), "login");

    // Assert
    assertFalse(responses.containsKey("401"));
  }

  @Test
  @DisplayName("Codes are grouped by their status, common ones first, then the listed ones in order")
  void codesGroupedByStatus() throws Exception {
    // Act
    Map<String, String> responses = responsesOf(new FakeThingController(), "update", Long.class, String.class);

    // Assert
    assertEquals(Map.of(
        "400", "`VALIDATION_FAILED`, `STUDENT_NOT_ENROLLED`",
        "401", "`UNAUTHENTICATED`",
        "403", "`ACCESS_DENIED`, `CSRF_TOKEN_INVALID`",
        "404", "`NOT_FOUND`",
        "409", "`ASSIGNMENT_ALREADY_EXISTS`"), responses);
  }

  @Test
  @DisplayName("A listed code that's already a common one (ACCESS_DENIED) isn't shown twice")
  void listedCommonCodeNotRepeated() throws Exception {
    // Act
    Map<String, String> responses = responsesOf(new FakeThingController(), "update", Long.class, String.class);

    // Assert
    assertEquals(1, responses.get("403").split("ACCESS_DENIED", -1).length - 1);
  }

  @Test
  @DisplayName("Error responses point to the ErrorResponseDTO schema")
  void errorResponsesUseErrorSchema() throws Exception {
    // Arrange
    HandlerMethod handler = new HandlerMethod(new FakeThingController(),
        FakeThingController.class.getMethod("list"));

    // Act
    ApiResponse response = config.errorCodesCustomizer().customize(new Operation(), handler).getResponses().get("401");

    // Assert
    assertEquals("#/components/schemas/ErrorResponseDTO",
        response.getContent().get("application/json").getSchema().get$ref());
  }

  @Test
  @DisplayName("The error schema and the field error inside it are added to the components")
  void errorSchemaIsAdded() {
    // Arrange
    OpenAPI openApi = new OpenAPI();

    // Act
    config.errorSchemaCustomizer().customise(openApi);

    // Assert
    assertTrue(openApi.getComponents().getSchemas().containsKey("ErrorResponseDTO"));
    assertTrue(openApi.getComponents().getSchemas().containsKey("FieldErrorDTO"));
  }

}
