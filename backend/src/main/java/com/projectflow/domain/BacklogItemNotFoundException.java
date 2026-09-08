package com.projectflow.domain;

public class BacklogItemNotFoundException extends RuntimeException {

    public BacklogItemNotFoundException(Long id) {
        super("Backlog 항목을 찾을 수 없습니다: " + id);
    }
}
