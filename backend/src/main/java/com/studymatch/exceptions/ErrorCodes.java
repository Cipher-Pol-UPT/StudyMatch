package com.studymatch.exceptions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// the codes an endpoint can send besides the common ones, shown in Swagger grouped by status
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ErrorCodes {

  ErrorCode[] value();

}
