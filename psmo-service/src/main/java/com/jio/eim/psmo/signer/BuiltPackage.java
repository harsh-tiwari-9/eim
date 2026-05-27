package com.jio.eim.psmo.signer;

import org.bouncycastle.asn1.ASN1Encodable;

public record BuiltPackage(
       ASN1Encodable header,
       ASN1Encodable command,
       byte[] toBeSigned
) {}