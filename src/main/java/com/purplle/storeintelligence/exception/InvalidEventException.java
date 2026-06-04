package com.purplle.storeintelligence.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a single event in a batch fails business-rule validation.
 */
public class InvalidEventException extends AppException {
    public InvalidEventException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
