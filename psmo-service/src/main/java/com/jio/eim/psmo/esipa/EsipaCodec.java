package com.jio.eim.psmo.esipa;

import java.io.IOException;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.BERTags;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.springframework.stereotype.Component;

/**
 * DER codec for the SGP.32 ESipa polling loop (GSMA SGP.32 v1.2 §6.3).
 *
 * <p>The ASN.1 module {@code SGP32Definitions} uses {@code AUTOMATIC TAGS}, so manually
 * tagged components are IMPLICIT, except tagged CHOICEs (e.g. {@code GetEimPackageResponse
 * ::= [79] CHOICE}) which are necessarily EXPLICIT. Only the polling-loop functions are
 * handled here; the RSP profile-download functions are out of scope.
 *
 * <p>Function selection is by the outer context tag of {@code EsipaMessageFromIpaToEim}.
 * The {@code eidValue} ({@code [APPLICATION 26]}, tag {@code '5A'}, 16 octets) is located by
 * a byte scan, which is robust to BouncyCastle's handling of implicit primitive tags.
 */
@Component
public class EsipaCodec {

    /** Function carried by an incoming EsipaMessageFromIpaToEim (polling loop only). */
    public enum Function { GET_EIM_PACKAGE, PROVIDE_EIM_PACKAGE_RESULT, HANDLE_NOTIFICATION, UNKNOWN }

    /** Decoded request: the selected function and the EID (hex, may be null if absent/unparseable). */
    public record DecodedRequest(Function function, String eidHex) {}

    // eimPackageError codes — GetEimPackageResponse, §6.3.2.6
    public static final int ERR_NO_PACKAGE_AVAILABLE = 1;
    public static final int ERR_EID_NOT_FOUND = 2;
    public static final int ERR_INVALID_EID = 3;
    public static final int ERR_MISSING_EID = 4;
    public static final int ERR_UNDEFINED = 127;

    // EsipaMessageFromIpaToEim / ...FromEimToIpa context tags (§6.3.1)
    private static final int TAG_HANDLE_NOTIFICATION = 61;       // BF3D
    private static final int TAG_GET_EIM_PACKAGE = 79;           // BF4F
    private static final int TAG_PROVIDE_EIM_PACKAGE_RESULT = 80; // BF50

    private static final int APP_TAG_EID = 0x5A;                 // [APPLICATION 26]
    private static final int EID_LEN = 16;

    public DecodedRequest decode(byte[] body) throws IOException {
        if (body == null || body.length == 0) {
            return new DecodedRequest(Function.UNKNOWN, null);
        }
        ASN1Primitive top = ASN1Primitive.fromByteArray(body);
        Function fn = Function.UNKNOWN;
        if (top instanceof ASN1TaggedObject t && t.getTagClass() == BERTags.CONTEXT_SPECIFIC) {
            fn = switch (t.getTagNo()) {
                case TAG_GET_EIM_PACKAGE -> Function.GET_EIM_PACKAGE;
                case TAG_PROVIDE_EIM_PACKAGE_RESULT -> Function.PROVIDE_EIM_PACKAGE_RESULT;
                case TAG_HANDLE_NOTIFICATION -> Function.HANDLE_NOTIFICATION;
                default -> Function.UNKNOWN;
            };
        }
        return new DecodedRequest(fn, findEid(body));
    }

    /**
     * EsipaMessageFromEimToIpa : getEimPackageResponse [79] CHOICE eimPackageError INTEGER.
     * Explicit [79] wrapper around a universal INTEGER, e.g. {@code BF4F 03 02 01 01}.
     */
    public byte[] encodeGetEimPackageError(int code) throws IOException {
        return new DERTaggedObject(true, BERTags.CONTEXT_SPECIFIC, TAG_GET_EIM_PACKAGE,
                new ASN1Integer(code)).getEncoded("DER");
    }

    /**
     * EsipaMessageFromEimToIpa : getEimPackageResponse [79] CHOICE euiccPackageRequest [81].
     * {@code euiccPackageRequestDer} is the full DER of EuiccPackageRequest (already tagged
     * [81]/'BF51'); it is wrapped in the explicit [79] response selector.
     */
    public byte[] encodeGetEimPackageResponse(byte[] euiccPackageRequestDer) throws IOException {
        ASN1Primitive inner = ASN1Primitive.fromByteArray(euiccPackageRequestDer);
        return new DERTaggedObject(true, BERTags.CONTEXT_SPECIFIC, TAG_GET_EIM_PACKAGE, inner)
                .getEncoded("DER");
    }

    /**
     * EsipaMessageFromEimToIpa : provideEimPackageResultResponse [80] CHOICE emptyResponse
     * SEQUENCE {}. Explicit [80] wrapper around an empty SEQUENCE, e.g. {@code BF50 02 30 00}.
     */
    public byte[] encodeProvideResultAck() throws IOException {
        return new DERTaggedObject(true, BERTags.CONTEXT_SPECIFIC, TAG_PROVIDE_EIM_PACKAGE_RESULT,
                new DERSequence()).getEncoded("DER");
    }

    /** Locate the 16-octet eidValue ([APPLICATION 26], tag '5A' length '10') by byte scan. */
    private static String findEid(byte[] der) {
        for (int i = 0; i + 2 + EID_LEN <= der.length; i++) {
            if ((der[i] & 0xFF) == APP_TAG_EID && (der[i + 1] & 0xFF) == EID_LEN) {
                StringBuilder sb = new StringBuilder(EID_LEN * 2);
                for (int j = 0; j < EID_LEN; j++) {
                    sb.append(String.format("%02X", der[i + 2 + j]));
                }
                return sb.toString();
            }
        }
        return null;
    }
}