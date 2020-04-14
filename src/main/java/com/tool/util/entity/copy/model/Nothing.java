package com.tool.util.entity.copy.model;

// This class is just for nothing
// When we use this in copy, we will just ignore it.
public final class Nothing {
    public static final Nothing instance = new Nothing();

    private Nothing() {
    }

    public boolean equals(Object other) {
        if (other == null) return false;
        if (other instanceof Nothing) return true;
        return false;
    }
    public int hashCode() {
        return super.hashCode();
    }
}