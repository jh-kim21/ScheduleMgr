package com.projectflow.domain;

/**
 * Raised when a dependency is rejected for a reason other than a cycle — a self-link, a duplicate
 * of an existing pair, or an endpoint outside the project.
 */
public class InvalidDependencyException extends RuntimeException {

    public InvalidDependencyException(String message) {
        super(message);
    }
}
