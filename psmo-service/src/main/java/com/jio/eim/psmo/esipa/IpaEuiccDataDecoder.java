package com.jio.eim.psmo.esipa;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.BERTags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Decodes an {@code IpaEuiccDataResponse} ([82] / BF52) — the reply to an {@code EUICC_DATA}
 * ({@code IpaEuiccDataRequest}) operation. Verified against SGP.32 v1.2 §2.11.2.2:
 *
 * <pre>
 * IpaEuiccDataResponse ::= [82] CHOICE { ipaEuiccData IpaEuiccData, ipaEuiccDataResponseError ... }
 * IpaEuiccData ::= SEQUENCE {
 *     defaultSmdpAddress [1] UTF8String OPTIONAL,   -- 81
 *     euiccInfo2        [34] EUICCInfo2 OPTIONAL,    -- BF22 { profileVersion[1], svn[2], euiccFirmwareVer[3], … }
 *     rootSmdsAddress    [3] UTF8String OPTIONAL,    -- 83
 *     eimTransactionId   [7] TransactionId OPTIONAL, -- 87  (echoes our request's [3] -> operationId)
 *     deviceInfo         [9] DeviceInfo OPTIONAL,    -- A9  { tac, … }
 *     … (notificationsList, euiccInfo1, certs, ipaCapabilities also possible) }
 * </pre>
 *
 * Only the fields the device-info UI needs are extracted; unknown fields are ignored (the SEQUENCE is
 * extensible). The response is correlated to its {@link com.jio.eim.psmo.entity.Operation} via the
 * echoed {@code eimTransactionId}, exactly like {@link EuiccPackageResultDecoder}.
 */
@Component
@Slf4j
public class IpaEuiccDataDecoder {

    // IpaEuiccData field tags.
    private static final int TAG_DEFAULT_SMDP_ADDRESS = 1;   // 81
    private static final int TAG_ROOT_SMDS_ADDRESS = 3;      // 83
    private static final int TAG_EIM_TRANSACTION_ID = 7;     // 87
    private static final int TAG_EUICC_INFO2 = 34;           // BF22
    private static final int TAG_DEVICE_INFO = 9;            // A9
    // EUICCInfo2 field tags.
    private static final int TAG_PROFILE_VERSION = 1;
    private static final int TAG_SVN = 2;
    private static final int TAG_EUICC_FIRMWARE_VER = 3;

    /**
     * @param operationId the echoed {@code eimTransactionId} (null if absent — cannot correlate)
     * @param success     true when the response carried {@code ipaEuiccData}; false for an error
     * @param errorCode   the {@code ipaEuiccDataResponseError} code when {@code success} is false
     * @param details     the extracted eUICC-data fields, stored as the operation's result payload
     */
    public record Decoded(Long operationId, boolean success, Integer errorCode, Map<String, Object> details) {}

    public Decoded decode(byte[] ipaEuiccDataResponseDer) {
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            // IpaEuiccDataResponse ::= [82] CHOICE -> EXPLICIT tag wrapping the chosen alternative.
            ASN1TaggedObject top = (ASN1TaggedObject) ASN1Primitive.fromByteArray(ipaEuiccDataResponseDer);
            ASN1Primitive inner = top.getExplicitBaseObject().toASN1Primitive();

            // Locate the ipaEuiccData SEQUENCE (success) — tolerate a [0]-wrapped alternative.
            ASN1Sequence dataSeq = null;
            if (inner instanceof ASN1Sequence s) {
                dataSeq = s;
            } else if (inner instanceof ASN1TaggedObject t) {
                if (t.getTagNo() == 0) {
                    dataSeq = ASN1Sequence.getInstance(t, false);   // ipaEuiccData alternative
                } else {
                    Integer errorCode = intValue(t);                // ipaEuiccDataResponseError
                    log.warn("IpaEuiccDataResponse error, code={}", errorCode);
                    details.put("ipaEuiccDataError", errorCode);
                    return new Decoded(null, false, errorCode, details);
                }
            }
            if (dataSeq == null) {
                log.warn("IpaEuiccDataResponse: unrecognised inner {}", inner.getClass().getSimpleName());
                return new Decoded(null, false, null, details);
            }

            Long operationId = null;
            for (ASN1Encodable el : dataSeq) {
                if (!(el instanceof ASN1TaggedObject f) || f.getTagClass() != BERTags.CONTEXT_SPECIFIC) {
                    continue;
                }
                switch (f.getTagNo()) {
                    case TAG_DEFAULT_SMDP_ADDRESS -> details.put("defaultSmdpAddress", utf8(f));
                    case TAG_ROOT_SMDS_ADDRESS -> details.put("rootSmdsAddress", utf8(f));
                    case TAG_EIM_TRANSACTION_ID -> operationId = octetsToLong(octets(f));
                    case TAG_EUICC_INFO2 -> putEuiccInfo2(f, details);
                    case TAG_DEVICE_INFO -> putDeviceInfo(f, details);
                    default -> { /* notificationsList, euiccInfo1, certs, ipaCapabilities: ignored */ }
                }
            }
            return new Decoded(operationId, true, null, details);
        } catch (Exception ex) {
            log.warn("Failed to decode IpaEuiccDataResponse", ex);
            return new Decoded(null, false, null, details);
        }
    }

    /** EUICCInfo2 [34]/BF22 -> profileVersion, svn, euiccFirmwareVer (each VersionType = 3-byte x.y.z). */
    private void putEuiccInfo2(ASN1TaggedObject euiccInfo2, Map<String, Object> details) {
        try {
            ASN1Sequence seq = ASN1Sequence.getInstance(euiccInfo2, false);
            for (ASN1Encodable el : seq) {
                if (!(el instanceof ASN1TaggedObject f) || f.getTagClass() != BERTags.CONTEXT_SPECIFIC) {
                    continue;
                }
                switch (f.getTagNo()) {
                    case TAG_PROFILE_VERSION -> details.put("profileVersion", version(octets(f)));
                    case TAG_SVN -> details.put("svn", version(octets(f)));
                    case TAG_EUICC_FIRMWARE_VER -> details.put("euiccFirmwareVer", version(octets(f)));
                    default -> { /* other EUICCInfo2 fields ignored */ }
                }
            }
        } catch (Exception ex) {
            log.debug("Could not parse EUICCInfo2", ex);
        }
    }

    /** DeviceInfo [9]/A9 -> TAC (first OCTET STRING, BCD-encoded, 8 digits). Best-effort. */
    private void putDeviceInfo(ASN1TaggedObject deviceInfo, Map<String, Object> details) {
        try {
            ASN1Sequence seq = ASN1Sequence.getInstance(deviceInfo, false);
            for (ASN1Encodable el : seq) {
                if (el.toASN1Primitive() instanceof ASN1OctetString oct) {
                    details.put("tac", bcdDigits(oct.getOctets()));
                    return;  // tac is the first element
                }
            }
        } catch (Exception ex) {
            log.debug("Could not parse DeviceInfo", ex);
        }
    }

    /** VersionType OCTET STRING (e.g. {@code 02 03 01}) -> {@code "2.3.1"}. */
    private static String version(byte[] v) {
        StringBuilder sb = new StringBuilder(v.length * 2);
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append('.');
            sb.append(v[i] & 0xFF);
        }
        return sb.toString();
    }

    private static String bcdDigits(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String utf8(ASN1TaggedObject t) {
        return ((ASN1String) t.getBaseUniversal(false, BERTags.UTF8_STRING)).getString();
    }

    private static byte[] octets(ASN1TaggedObject t) {
        return ((ASN1OctetString) t.getBaseUniversal(false, BERTags.OCTET_STRING)).getOctets();
    }

    private static Integer intValue(ASN1TaggedObject t) {
        try {
            return ((ASN1Integer) t.getBaseUniversal(false, BERTags.INTEGER)).intValueExact();
        } catch (Exception ex) {
            return null;
        }
    }

    private static long octetsToLong(byte[] bytes) {
        long v = 0;
        for (byte b : bytes) {
            v = (v << 8) | (b & 0xFF);
        }
        return v;
    }
}
