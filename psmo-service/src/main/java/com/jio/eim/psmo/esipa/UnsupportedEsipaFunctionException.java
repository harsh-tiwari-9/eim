package com.jio.eim.psmo.esipa;

/**
 * Raised when the IPA sends an ESipa function this eIM does not (yet) implement — e.g. the
 * profile-download handshake ({@code initiateAuthentication}, {@code authenticateClient},
 * {@code getBoundProfilePackage}). Polling-related functions (getEimPackage,
 * provideEimPackageResult) are supported.
 */
public class UnsupportedEsipaFunctionException extends RuntimeException {

    private final int tagNo;

    public UnsupportedEsipaFunctionException(int tagNo) {
        super("Unsupported ESipa function with context tag [" + tagNo + "]");
        this.tagNo = tagNo;
    }

    public int getTagNo() {
        return tagNo;
    }
}