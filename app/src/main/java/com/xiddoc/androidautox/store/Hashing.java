package com.xiddoc.androidautox.store;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 helpers. */
public final class Hashing {

    /** Overridable in tests so the (otherwise unreachable) missing-algorithm path can run. */
    static volatile String algorithm = "SHA-256";

    private Hashing() {}

    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static String sha256Hex(byte[] data) {
        return hex(sha256().digest(data));
    }
}
