package com.yon.arsc.exception;

public class InvalidArscException extends Exception{
    public InvalidArscException(Throwable cause) {
        super(cause);
    }

    public InvalidArscException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidArscException(String message) {
        super(message);
    }

    public InvalidArscException() {
    }
}
