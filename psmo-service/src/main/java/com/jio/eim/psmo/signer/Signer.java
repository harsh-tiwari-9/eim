package com.jio.eim.psmo.signer;

public interface Signer {
    SignatureResult sign(byte[] toBeSigned);

    record SignatureResult(String algorithm, byte[] signature) {}
}