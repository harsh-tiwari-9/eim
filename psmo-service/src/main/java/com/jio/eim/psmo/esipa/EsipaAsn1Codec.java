package com.jio.eim.psmo.esipa;

import java.io.IOException;
import java.util.List;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.BERTags;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
git stimport org.bouncycastle.util.encoders.Hex;
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