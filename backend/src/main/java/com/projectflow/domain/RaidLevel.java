package com.projectflow.domain;

/** Three-step scale used for both probability and impact, and for the exposure band. */
public enum RaidLevel {
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    private final int weight;

    RaidLevel(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
