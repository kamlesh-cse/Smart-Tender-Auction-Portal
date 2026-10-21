package com.smartportal.exception;

public class ForbiddenException extends IllegalStateException {

    public ForbiddenException(String message) {
        super(message);
    }
}
