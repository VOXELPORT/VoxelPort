package org.localm.model;

public record RamPreset(String label, int mb) {
    @Override
    public String toString() {
        return label;
    }
}
