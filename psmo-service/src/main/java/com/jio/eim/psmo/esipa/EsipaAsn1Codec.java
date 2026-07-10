package com.jio.eim.psmo.esipa;

import java.io.IOException;
import java.util.List;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.BERTags;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.util.encoders.Hex;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DER codec for the SGP.32 ESipa interface (interface ES_eim, section 6.3).
 *
 * <p>Only the eIM-Package retrieval/result functions needed for IPA polling are implemented:
 * <ul>
 *   <li>{@code getEimPackageRequest  [79]} (BF4F) -> {@code getEimPackageResponse  [79]} (BF4F)</li>
 *   <li>{@code provideEimPackageResult [80]} (BF50) -> {@code provideEimPackageResultResponse [80]} (BF50)</li>
 * </ul>
 *
 * <p>The module is {@code AUTOMATIC TAGS / EXTENSIBILITY IMPLIED}; because every relevant SEQUENCE
 * carries an explicit {@code [n]} tag, automatic tagging is disabled and the manual context tags
 * drive the {@code BFxx} encodings. {@code GetEimPackageRequest ::= [79] SEQUENCE} and
 * {@code ProvideEimPackageResultResponse ::= [80] SEQUENCE} are IMPLICITly tagged (the context tag
 * replaces the universal SEQUENCE tag). {@code GetEimPackageResponse ::= [79] CHOICE} is EXPLICITly
 * tagged because tagging a CHOICE is always explicit.
 */
@Component
@Slf4j
public class EsipaAsn1Codec {

    /** Context tag of getEimPackage{Request,Response} (BF4F). */
    static final int TAG_GET_EIM_PACKAGE = 79;
    /** Context tag of provideEimPackageResult{,Response} (BF50). */
    static final int TAG_PROVIDE_EIM_PACKAGE_RESULT = 80;
    /** Context tag of initiateAuthentication{Request,Response}Esipa (BF39). */
    public static final int TAG_INITIATE_AUTHENTICATION = 57;
    /** Context tag of getBoundProfilePackage{Request,Response}Esipa (BF3A). */
    public static final int TAG_GET_BOUND_PROFILE_PACKAGE = 58;
    /** Context tag of authenticateClient{Request,Response}Esipa (BF3B). */
    public static final int TAG_AUTHENTICATE_CLIENT = 59;
    /** Context tag of handleNotificationEsipa (BF3D). */
    public static final int TAG_HANDLE_NOTIFICATION = 61;
    /** EUICCInfo1 ::= [32] SEQUENCE (BF20). */
    private static final int TAG_EUICC_INFO1 = 32;
    /** Context tag of eimAcknowledgements (BF53). */
    private static final int TAG_EIM_ACKNOWLEDGEMENTS = 83;
    /** Context tag of SequenceNumber ([0] INTEGER). */
    private static final int TAG_SEQUENCE_NUMBER = 0;
    /** APPLICATION class tag of eidValue (5A). */
    private static final int TAG_EID_VALUE = 26;
    /** eimPackageError value returned when the device has no pending eIM Package. */
    private static final int NO_EIM_PACKAGE_AVAILABLE = 1;

    /** Parsed view of an EsipaMessageFromIpaToEim. */
    public record IpaMessage(Kind kind, String eidHex, byte[] eimPackageResult) {
        public enum Kind { GET_EIM_PACKAGE, PROVIDE_EIM_PACKAGE_RESULT }
    }

    /**
     * Decodes an {@code EsipaMessageFromIpaToEim} (the HTTP request body of {@code POST gsma/rsp2/asn1}).
     *
     * @throws UnsupportedEsipaFunctionException if the message is a valid ESipa function this eIM
     *         does not implement (e.g. the profile-download handshake functions).
     */
    public IpaMessage decodeFromIpa(byte[] body) {
        ASN1TaggedObject top = asContextTagged(parse(body));
        return switch (top.getTagNo()) {
            case TAG_GET_EIM_PACKAGE -> decodeGetEimPackageRequest(top);
            case TAG_PROVIDE_EIM_PACKAGE_RESULT -> decodeProvideEimPackageResult(top);
            default -> {
                // TEMP diagnostic: dump the raw body so an unimplemented ESipa function (e.g. the
                // profile-download relay handshake, tag [57]) can be decoded offline.
                log.warn("Unsupported ESipa function tag [{}] ({} bytes) — raw body: {}",
                        top.getTagNo(), body.length, Hex.toHexString(body));
                throw new UnsupportedEsipaFunctionException(top.getTagNo());
            }
        };
    }

    private IpaMessage decodeGetEimPackageRequest(ASN1TaggedObject tagged) {
        // GetEimPackageRequest ::= [79] SEQUENCE { eidValue [APPLICATION 26] Octet16, ... }
        ASN1Sequence seq = ASN1Sequence.getInstance(tagged, false);
        String eid = extractEidHex(seq);
        if (eid == null) {
            throw new IllegalArgumentException("getEimPackageRequest missing eidValue");
        }
        return new IpaMessage(IpaMessage.Kind.GET_EIM_PACKAGE, eid, null);
    }

    private IpaMessage decodeProvideEimPackageResult(ASN1TaggedObject tagged) {
        // ProvideEimPackageResult ::= [80] SEQUENCE { eidValue [APPLICATION 26] OPTIONAL, eimPackageResult }
        ASN1Sequence seq = ASN1Sequence.getInstance(tagged, false);
        String eid = extractEidHex(seq);
        byte[] result = null;
        for (ASN1Encodable el : seq) {
            if (isEidValue(el)) {
                continue;
            }
            try {
                result = el.toASN1Primitive().getEncoded("DER");
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to read eimPackageResult", ex);
            }
        }
        return new IpaMessage(IpaMessage.Kind.PROVIDE_EIM_PACKAGE_RESULT, eid, result);
    }

    /**
     * Encodes an {@code EsipaMessageFromEimToIpa} carrying {@code getEimPackageResponse}.
     *
     * @param euiccPackageRequest a DER-encoded {@code EuiccPackageRequest} ([81] / BF51), or
     *        {@code null} to signal {@code eimPackageError = noEimPackageAvailable(1)}.
     */
    public byte[] encodeGetEimPackageResponse(byte[] euiccPackageRequest) {
        ASN1Encodable inner = (euiccPackageRequest == null)
                ? new ASN1Integer(NO_EIM_PACKAGE_AVAILABLE)
                : parse(euiccPackageRequest);
        // GetEimPackageResponse ::= [79] CHOICE -> EXPLICIT context tag wrapping the chosen alternative.
        return encode(new DERTaggedObject(true, TAG_GET_EIM_PACKAGE, inner));
    }

    /**
     * Encodes {@code ProvideEimPackageResultResponse ::= [80] SEQUENCE { eimAcknowledgements [83]
     * OPTIONAL }}. Each acknowledged {@code seqNumber} tells the eUICC to delete that stored result
     * and stop re-delivering it. An empty/null list produces the bare {@code BF50 00} response.
     *
     * <pre>
     * EimAcknowledgements ::= [83] SEQUENCE OF SequenceNumber   -- Tag 'BF53'
     * SequenceNumber      ::= [0] INTEGER
     * </pre>
     */
    public byte[] encodeProvideEimPackageResultResponse(List<Integer> ackedSeqNumbers) {
        ASN1EncodableVector responseContent = new ASN1EncodableVector();
        if (ackedSeqNumbers != null && !ackedSeqNumbers.isEmpty()) {
            ASN1EncodableVector seqNumbers = new ASN1EncodableVector();
            for (Integer seqNumber : ackedSeqNumbers) {
                seqNumbers.add(new DERTaggedObject(false, TAG_SEQUENCE_NUMBER, new ASN1Integer(seqNumber)));
            }
            responseContent.add(new DERTaggedObject(false, TAG_EIM_ACKNOWLEDGEMENTS, new DERSequence(seqNumbers)));
        }
        // IMPLICIT context tag replacing the universal SEQUENCE tag.
        return encode(new DERTaggedObject(false, TAG_PROVIDE_EIM_PACKAGE_RESULT, new DERSequence(responseContent)));
    }

    /** Top-level context tag of an EsipaMessageFromIpaToEim (used to route relay functions). */
    public int topTag(byte[] body) {
        return asContextTagged(parse(body)).getTagNo();
    }

    /** Decoded {@code InitiateAuthenticationRequestEsipa [57]} (SGP.32 §6.3.2.1). */
    public record InitiateAuthRequest(byte[] euiccChallenge, String smdpAddress,
                                      byte[] euiccInfo1Der, Long eimTransactionId) {}

    /**
     * Decodes {@code InitiateAuthenticationRequestEsipa ::= [57] SEQUENCE { euiccChallenge [1],
     * smdpAddress [3] OPTIONAL, euiccInfo1 EUICCInfo1 OPTIONAL, eimTransactionId [2] OPTIONAL }}.
     * {@code euiccInfo1} is returned as its full DER ([32]/BF20) to forward verbatim to ES9+.
     */
    public InitiateAuthRequest decodeInitiateAuthRequest(byte[] body) {
        ASN1TaggedObject top = asContextTagged(parse(body));
        if (top.getTagNo() != TAG_INITIATE_AUTHENTICATION) {
            throw new IllegalArgumentException("Not initiateAuthenticationRequestEsipa: [" + top.getTagNo() + "]");
        }
        ASN1Sequence seq = ASN1Sequence.getInstance(top, false);
        byte[] euiccChallenge = null;
        String smdpAddress = null;
        byte[] euiccInfo1 = null;
        Long eimTransactionId = null;
        for (ASN1Encodable el : seq) {
            if (!(el instanceof ASN1TaggedObject t) || t.getTagClass() != BERTags.CONTEXT_SPECIFIC) {
                continue;
            }
            switch (t.getTagNo()) {
                case 1 -> euiccChallenge = octetsOf(t);
                case 3 -> smdpAddress = ((ASN1String) t.getBaseUniversal(false, BERTags.UTF8_STRING)).getString();
                case TAG_EUICC_INFO1 -> euiccInfo1 = derOf(t);
                case 2 -> eimTransactionId = octetsToLong(octetsOf(t));
                default -> { /* ignore */ }
            }
        }
        return new InitiateAuthRequest(euiccChallenge, smdpAddress, euiccInfo1, eimTransactionId);
    }

    /**
     * Encodes the success response {@code EsipaMessageFromEimToIpa -> initiateAuthenticationResponseEsipa
     * [57] -> initiateAuthenticationOkEsipa}. The five field byte arrays are the DER of each ASN.1
     * object as returned by ES9+ (base64-decoded by the caller); {@code transactionId} is its raw
     * octets. {@code [57]} wraps a CHOICE so it is EXPLICIT, and the "ok" alternative is context [0].
     */
    public byte[] encodeInitiateAuthOk(byte[] transactionId, byte[] serverSigned1Der,
            byte[] serverSignature1Der, byte[] euiccCiPKIdDer, byte[] serverCertificateDer) {
        ASN1EncodableVector ok = new ASN1EncodableVector();
        if (transactionId != null) {
            ok.add(new DERTaggedObject(false, 0, new DEROctetString(transactionId)));  // transactionId [0]
        }
        ok.add(parse(serverSigned1Der));         // serverSigned1
        ok.add(parse(serverSignature1Der));      // serverSignature1 [APPLICATION 55] (5F37 TLV)
        ok.add(parse(euiccCiPKIdDer));           // euiccCiPKIdentifierToBeUsed OCTET STRING (04 TLV)
        ok.add(parse(serverCertificateDer));     // serverCertificate
        ASN1Encodable okChoice = new DERTaggedObject(false, 0, new DERSequence(ok)); // ok alt -> [0]
        return encode(new DERTaggedObject(true, TAG_INITIATE_AUTHENTICATION, okChoice)); // [57] EXPLICIT
    }

    /** Encodes {@code initiateAuthenticationResponseEsipa} error alternative (context [1] INTEGER). */
    public byte[] encodeInitiateAuthError(int errorCode) {
        ASN1Encodable errChoice = new DERTaggedObject(false, 1, new ASN1Integer(errorCode));
        return encode(new DERTaggedObject(true, TAG_INITIATE_AUTHENTICATION, errChoice));
    }

    private static byte[] octetsOf(ASN1TaggedObject t) {
        return ((ASN1OctetString) t.getBaseUniversal(false, BERTags.OCTET_STRING)).getOctets();
    }

    private static byte[] derOf(ASN1Encodable el) {
        try {
            return el.toASN1Primitive().getEncoded("DER");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to DER-encode element", ex);
        }
    }

    private static long octetsToLong(byte[] bytes) {
        long v = 0;
        for (byte b : bytes) {
            v = (v << 8) | (b & 0xFF);
        }
        return v;
    }

    private String extractEidHex(ASN1Sequence seq) {
        for (ASN1Encodable el : seq) {
            if (isEidValue(el)) {
                // eidValue is [APPLICATION 26] IMPLICIT Octet16; resolve the underlying OCTET STRING.
                ASN1OctetString eid = (ASN1OctetString) ((ASN1TaggedObject) el)
                        .getBaseUniversal(false, BERTags.OCTET_STRING);
                return bytesToHex(eid.getOctets());
            }
        }
        return null;
    }

    private static boolean isEidValue(ASN1Encodable el) {
        return el instanceof ASN1TaggedObject t
                && t.getTagClass() == BERTags.APPLICATION
                && t.getTagNo() == TAG_EID_VALUE;
    }

    private static ASN1TaggedObject asContextTagged(ASN1Primitive prim) {
        if (prim instanceof ASN1TaggedObject t && t.getTagClass() == BERTags.CONTEXT_SPECIFIC) {
            return t;
        }
        throw new IllegalArgumentException("Not a valid EsipaMessageFromIpaToEim envelope");
    }

    private static ASN1Primitive parse(byte[] der) {
        try {
            return ASN1Primitive.fromByteArray(der);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Malformed DER in ESipa message", ex);
        }
    }

    private static byte[] encode(ASN1Encodable obj) {
        try {
            return obj.toASN1Primitive().getEncoded("DER");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to DER-encode ESipa response", ex);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString().toUpperCase();
    }
}
