package com.projectflow.domain;

public class WbsItemNotFoundException extends RuntimeException {

    public WbsItemNotFoundException(Long id) {
        super("WBS item not found: id=" + id);
    }
}
