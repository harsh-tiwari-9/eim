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

    /**
     * Builds a spec {@code IpaEuiccDataRequest} ([82] / BF52) for an EUICC_DATA operation — a read of
     * the IPA/eUICC's own data (EID, EUICCInfo1/2, configured SM-DP+/SM-DS addresses, …). Like the
     * download trigger it is NOT signed: the IPA answers it directly, returning an {@code IpaEuiccData}
     * in its {@code ProvideEimPackageResult} on a later poll.
     */
    byte[] buildIpaEuiccDataRequest(PsmoCommandMessage message);
}
