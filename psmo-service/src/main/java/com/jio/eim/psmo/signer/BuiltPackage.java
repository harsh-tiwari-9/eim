package com.jio.eim.psmo.signer;

import org.bouncycastle.asn1.ASN1Encodable;

/**
 * Intermediate result of building an SGP.32 eUICC Package: the {@code EuiccPackageSigned}
 * data object and its DER encoding (the bytes the eIM signs to produce {@code eimSignature}).
 */
public record BuiltPackage(
       ASN1Encodable euiccPackageSigned,
       byte[] toBeSigned
) {}