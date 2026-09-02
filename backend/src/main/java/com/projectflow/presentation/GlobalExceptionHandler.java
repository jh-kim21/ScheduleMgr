package com.projectflow.presentation;

import com.projectflow.domain.CircularDependencyException;
import com.projectflow.domain.InvalidDependencyException;
import com.projectflow.domain.InvalidImportException;
import com.projectflow.domain.InvalidRaciAssignmentException;
import com.projectflow.domain.InvalidWbsHierarchyException;
import com.projectflow.domain.ProjectMemberNotFoundException;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.RaidItemNotFoundException;
import com.projectflow.domain.WbsItemNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            ProjectNotFoundException.class,
            WbsItemNotFoundException.class,
            ProjectMemberNotFoundException.class,
            RaidItemNotFoundException.class,
    })
    public ResponseEntity<Map<String, Object>> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    /** Structural rejections: an invalid move, or a dependency that is circular or otherwise unusable. */
    @ExceptionHandler({
            InvalidWbsHierarchyException.class,
            InvalidDependencyException.class,
            CircularDependencyException.class,
            InvalidRaciAssignmentException.class,
            InvalidImportException.class,
    })
    public ResponseEntity<Map<String, Object>> handleInvalidStructure(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    /**
     * Spring's own handling of an unparseable body answers 400 with no message, which reaches the
     * user as a bare "요청 실패 (400)". That is a dead end exactly when it matters most — importing
     * a file, where picking the wrong one is the likely mistake. The technical detail stays in the
     * log; the message here only has to say what kind of thing went wrong.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(HttpStatus.BAD_REQUEST,
                "요청 내용을 읽을 수 없습니다. 형식이 올바른 JSON인지 확인하세요."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(HttpStatus.BAD_REQUEST, message));
    }

    private Map<String, Object> errorBody(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
