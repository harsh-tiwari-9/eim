package com.jio.eim.psmo.signer;

import org.bouncycastle.asn1.ASN1Encodable;

/**
 * A built-but-unsigned SGP.32 eUICC Package.
 *
 * @param euiccPackageSigned the {@code EuiccPackageSigned} structure (DER-encoded into the final
 *        {@code EuiccPackageRequest} once the signature is attached)
 * @param toBeSigned         the exact bytes the eIM signs: {@code DER(euiccPackageSigned)}
 *                           concatenated with the {@code associationToken} data object
 */
public record BuiltPackage(
        ASN1Encodable euiccPackageSigned,
        byte[] toBeSigned
) {}