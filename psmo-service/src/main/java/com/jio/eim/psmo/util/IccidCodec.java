package com.jio.eim.psmo.util;

/**
 * Converts between a human-readable ICCID digit string and the on-card {@code Iccid} octet encoding
 * used by SGP.22/SGP.32 ({@code Iccid ::= OCTET STRING (SIZE(10))}).
 *
 * <p>Per ITU-T E.118 the ICCID is stored as BCD with each digit pair nibble-swapped, and an odd
 * number of digits padded with a trailing 'F' nibble. Example: {@code "12345"} encodes to bytes
 * {@code 0x21 0x43 0xF5}. This is the same swapped-BCD form the eUICC returns in
 * {@code ProfileInfo.iccid} (which {@code EuiccPackageResultDecoder} surfaces as hex today), so
 * {@link #toBytes} and {@link #toDigits} are exact inverses.
 */
public final class IccidCodec {

    private static final int MAX_DIGITS = 20;
    private static final int PAD_NIBBLE = 0x0F;

    private IccidCodec() {}

    /** Encodes a decimal ICCID (up to 20 digits) to its swapped-BCD octet string. */
    public static byte[] toBytes(String iccid) {
        if (iccid == null) {
            throw new IllegalArgumentException("ICCID must not be null");
        }
        String digits = iccid.trim();
        if (digits.isEmpty() || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new IllegalArgumentException("ICCID must be all decimal digits: " + iccid);
        }
        if (digits.length() > MAX_DIGITS) {
            throw new IllegalArgumentException("ICCID must be at most " + MAX_DIGITS + " digits: " + iccid);
        }
        // Pad to even length with a trailing 'F' nibble, then swap each digit pair.
        String padded = (digits.length() % 2 == 0) ? digits : digits + "F";
        byte[] out = new byte[padded.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int first = nibble(padded.charAt(2 * i));
            int second = nibble(padded.charAt(2 * i + 1));
            out[i] = (byte) ((second << 4) | first);
        }
        return out;
    }

    /** Decodes a swapped-BCD ICCID octet string back to its decimal digit string. */
    public static String toDigits(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("ICCID bytes must not be null");
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int low = b & 0x0F;
            int high = (b >> 4) & 0x0F;
            sb.append((char) ('0' + low));       // low nibble is the first (earlier) digit
            if (high != PAD_NIBBLE) {            // a trailing 'F' nibble is padding, not a digit
                sb.append((char) ('0' + high));
            }
        }
        return sb.toString();
    }

    private static int nibble(char c) {
        if (c == 'F' || c == 'f') {
            return PAD_NIBBLE;
        }
        return c - '0';
    }
}