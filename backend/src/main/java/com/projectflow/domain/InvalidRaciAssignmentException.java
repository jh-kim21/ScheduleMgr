package com.projectflow.domain;

/**
 * A RACI assignment that cannot be stored at all — a duplicate letter, or an endpoint belonging
 * to another project.
 *
 * <p>Rule breaches that <em>can</em> be stored (two Accountables, a task with no Responsible) are
 * not this: they are reported as issues on the matrix so the user can pass through them while
 * reorganising. See {@link RaciValidator}.
 */
public class InvalidRaciAssignmentException extends RuntimeException {

    public InvalidRaciAssignmentException(String message) {
        super(message);
    }
}
