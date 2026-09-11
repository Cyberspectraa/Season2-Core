package uk.co.cyberspectra.spectralmail;

import java.util.UUID;

/** Lightweight recipient descriptor sent to the client for the letter composer. */
public record ComposeRecipient(UUID uuid, String name) {
    public ComposeRecipient {
        if (uuid == null) throw new IllegalArgumentException("uuid");
        if (name == null || name.isBlank()) name = "Unknown";
    }
}
