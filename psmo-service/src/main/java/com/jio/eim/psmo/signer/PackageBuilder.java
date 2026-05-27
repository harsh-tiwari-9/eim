package com.jio.eim.psmo.signer;

import com.jio.eim.psmo.dto.PsmoCommandMessage;

public interface PackageBuilder {

    BuiltPackage build(PsmoCommandMessage message);

    byte[] attachSignature(BuiltPackage built, byte[] signature);
}
