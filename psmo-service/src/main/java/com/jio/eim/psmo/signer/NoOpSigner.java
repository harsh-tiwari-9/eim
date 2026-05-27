package com.jio.eim.psmo.signer;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("lab")
public class NoOpSigner implements Signer {

    public SignatureResult sign(byte[] toBeSigned) {
        return new SignatureResult("NONE", new byte[0]);
    }
}