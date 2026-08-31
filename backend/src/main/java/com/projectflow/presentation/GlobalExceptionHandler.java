package com.projectflow.presentation;

import com.projectflow.domain.CircularDependencyException;
import com.projectflow.domain.InvalidDependencyException;
import com.projectflow.domain.InvalidWbsHierarchyException;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.WbsItemNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ProjectNotFoundException.class, WbsItemNotFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    /** Structural rejections: an invalid move, or a dependency that is circular or otherwise unusable. */
    @ExceptionHandler({
            InvalidWbsHierarchyException.class,
            InvalidDependencyException.class,
            CircularDependencyException.class,
    })
    public ResponseEntity<Map<String, Object>> handleInvalidStructure(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(HttpStatus.BAD_REQUEST, ex.getMessage()));
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
