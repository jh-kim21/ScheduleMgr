package com.projectflow.domain;

public class SprintNotFoundException extends RuntimeException {

    public SprintNotFoundException(Long id) {
        super("Sprint를 찾을 수 없습니다: " + id);
    }
}
