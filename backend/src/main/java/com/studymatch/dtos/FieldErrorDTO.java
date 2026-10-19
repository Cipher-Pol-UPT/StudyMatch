package com.studymatch.dtos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.metadata.ConstraintDescriptor;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FieldErrorDTO(String field, String code, Map<String, Object> params) {

  private static final Set<String> IGNORED_ATTRIBUTES = Set.of("message", "groups", "payload", "regexp", "flags");

  public static FieldErrorDTO from(ConstraintViolation<?> violation) {
    return fromConstraint(violation.getPropertyPath().toString(), violation.getConstraintDescriptor());
  }

  // the annotation's name becomes the code (@NotBlank -> NOT_BLANK) and its limits become params
  public static FieldErrorDTO fromConstraint(String field, ConstraintDescriptor<?> descriptor) {
    String code = toUpperSnakeCase(descriptor.getAnnotation().annotationType().getSimpleName());
    Map<String, Object> params = new LinkedHashMap<>(descriptor.getAttributes());
    params.keySet().removeAll(IGNORED_ATTRIBUTES);
    if (code.equals("SIZE") && Integer.valueOf(0).equals(params.get("min"))) {
      code = "SIZE_MAX";
      params.remove("min");
    }
    return new FieldErrorDTO(field, code, params);
  }

  private static String toUpperSnakeCase(String name) {
    return name.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
  }

}
