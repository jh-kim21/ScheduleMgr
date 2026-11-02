package com.projectflow.domain;

/** A TODO that cannot be saved as asked, e.g. an assignee who is not a member of the project. */
public class InvalidTodoItemException extends RuntimeException {

    public InvalidTodoItemException(String message) {
        super(message);
    }
}
