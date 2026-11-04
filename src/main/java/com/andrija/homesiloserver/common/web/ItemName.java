package com.andrija.homesiloserver.common.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A valid file or folder name: not blank, at most 255 characters, no path
 * separators or control characters, and not "." or "..".
 */
@NotBlank(message = "Name must not be blank")
@Size(max = 255, message = "Name must not exceed 255 characters")
@Pattern(regexp = "^(?!\\.{1,2}$)[^/\\\\\\p{Cntrl}]+$", message = "Name contains invalid characters")
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ItemName {
    String message() default "Invalid name";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
