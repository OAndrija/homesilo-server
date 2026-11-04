package com.andrija.homesiloserver.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for errors that map directly to an HTTP status. The message is
 * safe to show to the client — never put internal details in it.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
