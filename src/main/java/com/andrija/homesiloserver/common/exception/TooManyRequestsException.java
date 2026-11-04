package com.andrija.homesiloserver.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.Duration;

@Getter
public class TooManyRequestsException extends ApiException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfter = retryAfter;
    }
}
