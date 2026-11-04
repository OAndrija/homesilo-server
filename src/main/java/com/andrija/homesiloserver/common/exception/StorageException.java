package com.andrija.homesiloserver.common.exception;

/** Unexpected I/O failure in the blob store. Mapped to 500; details are logged, not returned. */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
