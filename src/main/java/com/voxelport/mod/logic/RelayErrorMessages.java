package com.voxelport.mod.logic;

public final class RelayErrorMessages {
    private static final int MAX_CHAT_MESSAGE_LENGTH = 180;

    private RelayErrorMessages() {}

    public static String startFailure(Throwable error) {
        String message = usefulMessage(error);
        if (message.isBlank()) {
            message = error == null ? "unknown error" : error.getClass().getSimpleName();
        }

        message = redactSecrets(message)
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();

        if (message.length() > MAX_CHAT_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_CHAT_MESSAGE_LENGTH - 3).trim() + "...";
        }

        return "Failed to start VoxelPort: " + message;
    }

    private static String usefulMessage(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) return message;
            current = current.getCause();
        }
        return "";
    }

    private static String redactSecrets(String message) {
        return message.replaceAll("vp_[A-Za-z0-9_-]+", "vp_[redacted]");
    }
}
