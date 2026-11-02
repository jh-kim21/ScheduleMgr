package com.projectflow.domain;

public class TodoItemNotFoundException extends RuntimeException {

    public TodoItemNotFoundException(Long id) {
        super("TODO item not found: id=" + id);
    }
}
