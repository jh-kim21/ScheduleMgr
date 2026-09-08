package com.projectflow.domain;

/**
 * A Sprint operation that cannot be carried out: starting a second Sprint, assigning an item that
 * is not ready, closing something already closed, or completing an item that has not met its
 * completion conditions.
 */
public class InvalidSprintException extends RuntimeException {

    public InvalidSprintException(String message) {
        super(message);
    }
}
