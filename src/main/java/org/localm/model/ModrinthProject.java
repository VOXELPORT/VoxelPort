package org.localm.model;

public record ModrinthProject(String id, String slug, String title, String description, String author) {
    @Override public String toString() { return title; }
}
