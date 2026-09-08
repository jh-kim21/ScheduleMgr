package com.projectflow.domain;

/**
 * A Backlog entry that could not be stored as asked: a hierarchy that cannot exist (a Task with no
 * parent, a parent cycle), or a link to something that is not a Work Package.
 *
 * <p>Inconsistencies that merely need attention — an unlinked draft, a Waterfall Work Package —
 * are not this. They are reported by {@link BacklogAssessor} and shown on screen.
 */
public class InvalidBacklogItemException extends RuntimeException {

    public InvalidBacklogItemException(String message) {
        super(message);
    }
}
