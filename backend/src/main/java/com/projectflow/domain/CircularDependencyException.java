package com.projectflow.domain;

/** Raised when adding a dependency would make the predecessor graph cyclic (요구사항 6.3). */
public class CircularDependencyException extends RuntimeException {

    public CircularDependencyException(String message) {
        super(message);
    }
}
