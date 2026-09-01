package com.projectflow.domain;

public class RaidItemNotFoundException extends RuntimeException {

    public RaidItemNotFoundException(Long id) {
        super("RAID item not found: id=" + id);
    }
}
