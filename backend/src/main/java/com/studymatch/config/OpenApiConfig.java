package com.studymatch.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.method.HandlerMethod;

import com.studymatch.dtos.ErrorResponseDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.security.SecurityConfig;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

@Configuration
public class OpenApiConfig {

  private static final String ERROR_SCHEMA = "#/components/schemas/" + ErrorResponseDTO.class.getSimpleName();

  private static final String DESCRIPTION = """
      Backend of StudyMatch. Errors come back as `{ "code", "params", "fieldErrors" }` and the frontend \
      translates the code, so the lists below are codes, not messages.

      To try an endpoint, log in first with `POST /users/login` (the demo accounts are in the README). \
      Any endpoint that needs a login can also answer 403 with `ACCOUNT_BLOCKED`, `PASSWORD_CHANGE_REQUIRED` \
      or `PERSONALITY_TEST_REQUIRED`; those aren't repeated on every endpoint.""";

  @Bean
  public OpenAPI studyMatchApi() {
    return new OpenAPI().info(new Info().title("StudyMatch API").version("1.0").description(DESCRIPTION));
  }

  // added at the end, springdoc copies the bean above through JSON and the params map doesn't survive that
  @Bean
  public OpenApiCustomizer errorSchemaCustomizer() {
    return openApi -> {
      if (openApi.getComponents() == null) {
        openApi.setComponents(new Components());
      }
      ModelConverters.getInstance().readAll(ErrorResponseDTO.class).forEach(openApi.getComponents()::addSchemas);
    };
  }

  @Bean
  public OperationCustomizer errorCodesCustomizer() {
    return (operation, handlerMethod) -> {
      addErrorResponses(operation, errorCodesOf(handlerMethod));
      return operation;
    };
  }

  // the codes any endpoint like this one can get, then its own from @ErrorCodes
  private static List<ErrorCode> errorCodesOf(HandlerMethod handlerMethod) {
    RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), RequestMapping.class);
    boolean isGet = Arrays.asList(mapping.method()).contains(RequestMethod.GET);

    List<ErrorCode> codes = new ArrayList<>();
    String path = pathOf(handlerMethod, mapping);
    if (!SecurityConfig.PUBLIC_ENDPOINTS.contains(path) && !SecurityConfig.PUBLIC_GET_ENDPOINTS.contains(path)) {
      codes.add(ErrorCode.UNAUTHENTICATED);
    }
    if (handlerMethod.hasMethodAnnotation(PreAuthorize.class)) {
      codes.add(ErrorCode.ACCESS_DENIED);
    }
    if (!isGet) {
      codes.add(ErrorCode.CSRF_TOKEN_INVALID);
    }
    if (takesInput(handlerMethod)) {
      codes.add(ErrorCode.VALIDATION_FAILED);
    }

    ErrorCodes listed = handlerMethod.getMethodAnnotation(ErrorCodes.class);
    if (listed != null) {
      Arrays.stream(listed.value()).filter(code -> !codes.contains(code)).forEach(codes::add);
    }
    return codes;
  }

  // class path + method path, the same string SecurityConfig matches against
  private static String pathOf(HandlerMethod handlerMethod, RequestMapping mapping) {
    RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(),
        RequestMapping.class);
    String classPath = classMapping == null || classMapping.path().length == 0 ? "" : classMapping.path()[0];
    String methodPath = mapping.path().length == 0 ? "" : mapping.path()[0];
    return classPath + methodPath;
  }

  // a body, url value or file that can't be read is VALIDATION_FAILED (see GlobalExceptionHandler)
  private static boolean takesInput(HandlerMethod handlerMethod) {
    return Arrays.stream(handlerMethod.getMethodParameters()).anyMatch(parameter ->
        parameter.hasParameterAnnotation(RequestBody.class)
            || parameter.hasParameterAnnotation(PathVariable.class)
            || parameter.hasParameterAnnotation(RequestParam.class)
            || parameter.hasParameterAnnotation(RequestPart.class));
  }

  // one response per status, its description lists the codes
  private static void addErrorResponses(Operation operation, List<ErrorCode> codes) {
    Map<HttpStatus, List<ErrorCode>> byStatus = codes.stream()
        .collect(Collectors.groupingBy(ErrorCode::getStatus, TreeMap::new, Collectors.toList()));

    ApiResponses responses = operation.getResponses() == null ? new ApiResponses() : operation.getResponses();
    byStatus.forEach((status, statusCodes) ->
        responses.addApiResponse(String.valueOf(status.value()), errorResponse(statusCodes)));
    operation.setResponses(responses);
  }

  private static ApiResponse errorResponse(List<ErrorCode> codes) {
    String description = codes.stream().map(code -> "`" + code.name() + "`").collect(Collectors.joining(", "));
    Content content = new Content().addMediaType("application/json",
        new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA)));
    return new ApiResponse().description(description).content(content);
  }

}
