package com.projectflow.presentation;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.projectflow.domain.BacklogItemNotFoundException;
import com.projectflow.domain.CircularDependencyException;
import com.projectflow.domain.InvalidBacklogItemException;
import com.projectflow.domain.InvalidDependencyException;
import com.projectflow.domain.InvalidImportException;
import com.projectflow.domain.InvalidRaciAssignmentException;
import com.projectflow.domain.InvalidRaidLinkException;
import com.projectflow.domain.InvalidSprintException;
import com.projectflow.domain.InvalidWbsHierarchyException;
import com.projectflow.domain.ProjectMemberNotFoundException;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.RaidItemNotFoundException;
import com.projectflow.domain.SprintNotFoundException;
import com.projectflow.domain.WbsImportException;
import com.projectflow.domain.WbsItemNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            ProjectNotFoundException.class,
            WbsItemNotFoundException.class,
            ProjectMemberNotFoundException.class,
            RaidItemNotFoundException.class,
            BacklogItemNotFoundException.class,
            SprintNotFoundException.class,
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
            InvalidBacklogItemException.class,
            InvalidRaidLinkException.class,
            InvalidSprintException.class,
            WbsImportException.class,
    })
    public ResponseEntity<Map<String, Object>> handleInvalidStructure(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    /** A file bigger than {@code spring.servlet.multipart.max-file-size} would otherwise surface as a bare 500. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody(HttpStatus.BAD_REQUEST, "파일이 너무 큽니다 (최대 10MB)."));
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
                unknownEnumValue(ex).orElse("요청 내용을 읽을 수 없습니다. 형식이 올바른 JSON인지 확인하세요.")));
    }

    /**
     * A value outside an enum's constants arrives here as an unreadable body, which the generic
     * message above describes as bad JSON — true but useless, since the JSON is fine and one field
     * is wrong. Naming the field and listing what it accepts is the difference between a dead end
     * and a fixable error, and every enum-valued field in the API benefits (실행 방식, RAID 종류,
     * 프로젝트 상태 …).
     */
    private Optional<String> unknownEnumValue(HttpMessageNotReadableException ex) {
        if (!(ex.getCause() instanceof InvalidFormatException cause)) {
            return Optional.empty();
        }
        Class<?> target = cause.getTargetType();
        if (target == null || !target.isEnum()) {
            return Optional.empty();
        }
        String field = cause.getPath().isEmpty() ? "값"
                : cause.getPath().getLast().getFieldName();
        String allowed = Arrays.stream(target.getEnumConstants())
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
        return Optional.of("'%s'은(는) %s에 허용되지 않는 값입니다. 가능한 값: %s"
                .formatted(cause.getValue(), field, allowed));
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
