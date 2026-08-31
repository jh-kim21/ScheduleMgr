package com.projectflow.domain;

/**
 * Raised when a structural change would produce an invalid tree — moving an item into its
 * own subtree, or referencing a parent that belongs to another project.
 */
public class InvalidWbsHierarchyException extends RuntimeException {

    public InvalidWbsHierarchyException(String message) {
        super(message);
    }
}
