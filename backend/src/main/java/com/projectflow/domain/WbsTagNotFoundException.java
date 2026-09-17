package com.projectflow.domain;

public class WbsTagNotFoundException extends RuntimeException {

    public WbsTagNotFoundException(Long id) {
        super("WBS tag not found: id=" + id);
    }
}
