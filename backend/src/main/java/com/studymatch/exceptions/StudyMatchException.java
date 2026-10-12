package com.studymatch.exceptions;

import java.util.Map;

public class StudyMatchException extends RuntimeException {

  private final ErrorCode code;
  private final Map<String, Object> params;

  public StudyMatchException(ErrorCode code) {
    this(code, Map.of());
  }

  public StudyMatchException(ErrorCode code, Map<String, Object> params) {
    super(code.name());
    this.code = code;
    this.params = params;
  }

  public ErrorCode getCode() {
    return code;
  }

  public Map<String, Object> getParams() {
    return params;
  }

}
