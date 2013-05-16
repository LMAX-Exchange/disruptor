package com.lmax.disruptor;

/**
 * Exception thrown by the FatalExceptionHandler.
 */
public class FatalException extends RuntimeException {

    public FatalException(Throwable ex) {
        super(ex);
    }
}
