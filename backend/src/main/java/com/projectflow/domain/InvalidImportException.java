package com.projectflow.domain;

/**
 * The uploaded file cannot be turned into a project.
 *
 * <p>Import is the one place where the input is a file a person picked, possibly hand-edited or
 * produced by a different version. So it is checked and refused as a whole rather than partially
 * applied — half a project is worse than none, and the message has to say which part of the file
 * is wrong so it can be fixed.
 */
public class InvalidImportException extends RuntimeException {

    public InvalidImportException(String message) {
        super(message);
    }
}
