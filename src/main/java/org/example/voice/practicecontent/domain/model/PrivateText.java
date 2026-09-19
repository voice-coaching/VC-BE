package org.example.voice.practicecontent.domain.model;

/** A value whose persistence representation must be encrypted. */
public record PrivateText(String value) {
    @Override public String toString() { return "[private text]"; }
}
