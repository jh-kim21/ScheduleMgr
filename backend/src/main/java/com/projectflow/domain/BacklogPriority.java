package com.projectflow.domain;

/**
 * Backlog ordering priority. Declared most-urgent first so {@code ordinal()} sorts a list the way
 * the screen shows it, the same trick {@link RaidType} uses for its register sections.
 */
public enum BacklogPriority {
    HIGH,
    MEDIUM,
    LOW,
}
