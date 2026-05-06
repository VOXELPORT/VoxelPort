package org.localm.model;

public record ServerVersion(String label, String mcVersion, String url) {
    @Override
    public String toString() {
        return label;
    }
}
