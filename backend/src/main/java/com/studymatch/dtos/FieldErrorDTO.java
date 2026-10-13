package com.studymatch.dtos;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FieldErrorDTO(String field, String code, Map<String, Object> params) {

}
