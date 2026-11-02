package com.projectflow.application.dto;

import java.time.LocalDate;
import java.util.List;

/** 모든 변경 API가 목록 전체를 돌려준다. 기한 초과는 이 referenceDate 기준이다. */
public record TodoListResponse(LocalDate referenceDate, List<TodoItemResponse> items) {
}
