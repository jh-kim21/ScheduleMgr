package com.projectflow.domain;

/**
 * Raised when an uploaded WBS file (Excel/CSV) cannot be read or fails row-level validation.
 *
 * <p>Validation runs entirely before any row is inserted (같은 이유로 {@code InvalidImportException}과
 * 같은 태도) — the message lists every problem row found so the user fixes the file once instead of
 * one round trip per bad row, and a rejected file leaves the project's WBS untouched.
 */
public class WbsImportException extends RuntimeException {

    public WbsImportException(String message) {
        super(message);
    }
}
