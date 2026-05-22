package com.voxelport.mod.logic;

import java.util.regex.Pattern;

public final class VoxelPortCodec {
    private static final Pattern ROOM_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{6}$");

    private VoxelPortCodec() {}

    public static String encodeRoom(String roomCode) {
        return roomCode.toUpperCase();
    }

    public static String decodeRoom(String code) throws Exception {
        String normalized = code.trim().toUpperCase();
        if (!ROOM_CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid room code. Expected 6 uppercase alphanumeric characters (A-Z, 0-9).");
        }
        return normalized;
    }

    public static boolean isValidCode(String code) {
        if (code == null) return false;
        return ROOM_CODE_PATTERN.matcher(code.trim().toUpperCase()).matches();
    }
}
