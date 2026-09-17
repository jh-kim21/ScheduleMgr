package com.projectflow.domain;

/**
 * A tag operation the model refuses: a duplicate name, a tag from another project, or a change to
 * a Summary entry's retained tags.
 *
 * <p>Registered in {@code GlobalExceptionHandler} together with the other structural rejections —
 * a domain exception nobody maps there leaves every refusal going out as a 500.
 */
public class InvalidWbsTagException extends RuntimeException {

    public InvalidWbsTagException(String message) {
        super(message);
    }
}
