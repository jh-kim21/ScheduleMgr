package com.projectflow.domain;

/**
 * A RAID link that cannot exist: a target from another project, a target that is not there, or the
 * same target named twice.
 *
 * <p>{@code raid_links.target_id} has no foreign key — it points at three tables — so these are
 * refused in the service rather than by the database, and the UNIQUE index is a backstop rather
 * than the error the user sees.
 */
public class InvalidRaidLinkException extends RuntimeException {

    public InvalidRaidLinkException(String message) {
        super(message);
    }
}
