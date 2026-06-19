package com.jio.eim.psmo.signer;

import com.jio.eim.psmo.dto.PsmoCommandMessage;
import java.io.IOException;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.BERTags;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERUTF8String;
import org.springframework.stereotype.Component;

/**
 * Builds an SGP.32 {@code EuiccPackageRequest} (GSMA SGP.32 v1.2 §2.11.1.1) for a queued PSMO.
 *
 * <p>The {@code SGP32Definitions} module uses {@code AUTOMATIC TAGS}: manually tagged
 * components are IMPLICIT; the {@code EuiccPackage} CHOICE (no manual tags) is automatically
 * tagged, so {@code psmoList} becomes {@code [0]}.
 *
 * <p>LAB SCOPE: the {@code eimSignature} is supplied by the configured {@link Signer}
 * (the lab {@code NoOpSigner} produces an empty signature) and {@code counterValue} is a
 * placeholder. The structure is well-formed DER so a real eUICC can parse it, but it will
 * fail signature/counter verification until real eIM PKI is added.
 */
@Component
public class Asn1PackageBuilder implements PackageBuilder {

    private static final String EIM_ID_LAB = "lab-eim";

    // Psmo CHOICE tags (§2.11.1.1.3) and EuiccPackageRequest framing tags (§2.11.1.1)
    private static final int PSMO_ENABLE = 3;
    private static final int PSMO_DISABLE = 4;
    private static final int PSMO_DELETE = 5;
    private static final int PSMO_GET_RAT = 6;
    private static final int PSMO_LIST_PROFILE_INFO = 45;
    private static final int EUICC_PACKAGE_PSMO_LIST = 0;   // EuiccPackage CHOICE: psmoList [0]
    private static final int EUICC_PACKAGE_REQUEST = 81;    // EuiccPackageRequest [81] -> 'BF51'
    private static final int APP_TAG_EID_ICCID = 26;        // [APPLICATION 26] -> '5A'
    private static final int APP_TAG_EIM_SIGNATURE = 55;    // [APPLICATION 55] -> '5F37'
    private static final int EPS_EIM_ID = 0;                // EuiccPackageSigned: eimId [0]
    private static final int EPS_COUNTER_VALUE = 1;         // EuiccPackageSigned: counterValue [1]

    // Proprietary (non-SGP.32) placeholder for UPDATE_POLLING_INTERVAL — see project notes.
    private static final int PSMO_PROPRIETARY_POLLING = 120;

    @Override
    public BuiltPackage build(PsmoCommandMessage message) {
        try {
            ASN1Encodable psmo = buildPsmo(message);
            // EuiccPackage ::= CHOICE { psmoList SEQUENCE OF Psmo } -> auto-tagged [0]
            ASN1Encodable euiccPackage =
                    new DERTaggedObject(false, EUICC_PACKAGE_PSMO_LIST, new DERSequence(psmo));

            ASN1EncodableVector signed = new ASN1EncodableVector();
            signed.add(new DERTaggedObject(false, EPS_EIM_ID, new DERUTF8String(EIM_ID_LAB)));
            signed.add(new DERTaggedObject(false, BERTags.APPLICATION, APP_TAG_EID_ICCID,
                    new DEROctetString(hexToBytes(message.eid()))));
            signed.add(new DERTaggedObject(false, EPS_COUNTER_VALUE,
                    new ASN1Integer(message.operationId())));
            signed.add(euiccPackage);
            ASN1Encodable euiccPackageSigned = new DERSequence(signed);

            byte[] tbs = euiccPackageSigned.toASN1Primitive().getEncoded("DER");
            return new BuiltPackage(euiccPackageSigned, tbs);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to DER-encode EuiccPackageSigned", ex);
        }
    }

    @Override
    public byte[] attachSignature(BuiltPackage built, byte[] signature) {
        try {
            // EuiccPackageRequest ::= [81] SEQUENCE { euiccPackageSigned, eimSignature [APP 55] }
            ASN1EncodableVector req = new ASN1EncodableVector();
            req.add(built.euiccPackageSigned());
            req.add(new DERTaggedObject(false, BERTags.APPLICATION, APP_TAG_EIM_SIGNATURE,
                    new DEROctetString(signature == null ? new byte[0] : signature)));
            return new DERTaggedObject(false, BERTags.CONTEXT_SPECIFIC, EUICC_PACKAGE_REQUEST,
                    new DERSequence(req)).toASN1Primitive().getEncoded("DER");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to encode EuiccPackageRequest", ex);
        }
    }

    private ASN1Encodable buildPsmo(PsmoCommandMessage message) {
        return switch (message.type()) {
            // listProfileInfo [45] ProfileInfoListRequest ::= SEQUENCE { } (default tag list)
            case "AUDIT" -> new DERTaggedObject(false, PSMO_LIST_PROFILE_INFO, new DERSequence());
            case "ENABLE" -> profileOp(PSMO_ENABLE, message.targetIccid());
            case "DISABLE" -> profileOp(PSMO_DISABLE, message.targetIccid());
            case "DELETE" -> profileOp(PSMO_DELETE, message.targetIccid());
            // getRAT [6] SEQUENCE { } — used as a benign placeholder for DOWNLOAD (no PSMO equivalent)
            case "DOWNLOAD" -> new DERTaggedObject(false, PSMO_GET_RAT, new DERSequence());
            // Proprietary, non-SGP.32 — carries the interval; a real eUICC will reject it.
            case "UPDATE_POLLING_INTERVAL" -> new DERTaggedObject(false, PSMO_PROPRIETARY_POLLING,
                    new DERSequence(new ASN1Integer(pollingIntervalSeconds(message))));
            default -> throw new IllegalArgumentException(
                    "Unsupported PSMO type for ASN.1 encoder: " + message.type());
        };
    }

    /** enable/disable/delete [n] SEQUENCE { iccid [APPLICATION 26] Iccid }. */
    private ASN1Encodable profileOp(int psmoTag, String iccid) {
        if (iccid == null || iccid.isBlank()) {
            throw new IllegalArgumentException("targetIccid is required for this PSMO");
        }
        ASN1Encodable iccidObj = new DERTaggedObject(false, BERTags.APPLICATION, APP_TAG_EID_ICCID,
                new DEROctetString(packBcd(iccid)));
        return new DERTaggedObject(false, psmoTag, new DERSequence(iccidObj));
    }

    private long pollingIntervalSeconds(PsmoCommandMessage message) {
        Object raw = message.params() == null ? null : message.params().get("pollingIntervalSeconds");
        if (!(raw instanceof Number)) {
            throw new IllegalArgumentException(
                    "pollingIntervalSeconds missing for UPDATE_POLLING_INTERVAL op " + message.operationId());
        }
        return ((Number) raw).longValue();
    }

    private byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("EID hex must be even length: " + hex);
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex char in EID: " + hex);
            }
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /** ICCID BCD packing (nibble-swapped, F-padded for odd length) per SGP.22 Iccid encoding. */
    private byte[] packBcd(String digits) {
        String d = digits.length() % 2 == 0 ? digits : digits + "F";
        byte[] out = new byte[d.length() / 2];
        for (int i = 0; i < d.length(); i += 2) {
            int hi = digitOrF(d.charAt(i));
            int lo = digitOrF(d.charAt(i + 1));
            out[i / 2] = (byte) ((lo << 4) | hi);
        }
        return out;
    }

    private int digitOrF(char c) {
        if (c == 'F' || c == 'f') return 0xF;
        int v = Character.digit(c, 10);
        if (v < 0) throw new IllegalArgumentException("Invalid ICCID digit: " + c);
        return v;
    }
}