package com.jio.eim.psmo.signer;

import com.jio.eim.psmo.dto.PsmoCommandMessage;

public interface PackageBuilder {

    BuiltPackage build(PsmoCommandMessage message);

    byte[] attachSignature(BuiltPackage built, byte[] signature);

    /**
     * Builds a spec {@code ProfileDownloadTriggerRequest} ([84] / BF54) for a DOWNLOAD operation.
     * Unlike a {@code EuiccPackageRequest} this is NOT signed by the eIM — it merely tells the IPA
     * to start an RSP download; the download itself is authenticated between eUICC and SM-DP+.
     */
    byte[] buildProfileDownloadTrigger(PsmoCommandMessage message);
}
