package com.projectflow.application.dto;

/**
 * A project member named on a row that is not the RACI matrix.
 *
 * <p>Id and name only: the WBS tree shows who is responsible, it does not edit the assignment, so
 * it never needs the assignment id the matrix's cells carry. Changing who is responsible happens
 * on the RACI screen — there is exactly one write path for {@code raci_assignments}.
 */
public record MemberRef(Long memberId, String name) {
}
