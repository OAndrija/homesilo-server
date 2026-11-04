package com.andrija.homesiloserver.common.exception;

import org.springframework.http.HttpStatus;

public class QuotaExceededException extends ApiException {
    public QuotaExceededException(String message) {
        super(HttpStatus.valueOf(413), message);
    }
}
