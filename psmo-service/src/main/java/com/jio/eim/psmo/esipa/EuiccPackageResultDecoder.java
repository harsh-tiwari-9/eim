package com.jio.eim.psmo.esipa;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.BERTags;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;

/**
 * Decodes a SGP.32 {@code EuiccPackageResult} (the {@code eimPackageResult} carried in
 * {@code ESipa.ProvideEimPackageResult}) far enough to (a) link it to the originating Operation via
 * the echoed {@code eimTransactionId}, (b) decide success/failure, and (c) for AUDIT, surface the
 * returned profile list. Anything not understood is preserved as hex so nothing is silently lost.
 *
 * <pre>
 * EuiccPackageResult [81] CHOICE { euiccPackageResultSigned [0], euiccPackageErrorSigned [1], euiccPackageErrorUnsigned [2] }
 * euiccPackageResultSigned ::= SEQUENCE { euiccPackageResultDataSigned, euiccSignEPR [APP 55] }
 * euiccPackageResultDataSigned ::= SEQUENCE { eimId[0], counterValue[1], eimTransactionId[2]?, seqNumber[3], euiccResult SEQUENCE OF EuiccResultData }
 * EuiccResultData.listProfileInfoResult [45] ProfileInfoListResponse [45] CHOICE { profileInfoListOk [0] SEQUENCE OF ProfileInfo, profileInfoListError [1] }
 * </pre>
 */
@Component
public class EuiccPackageResultDecoder {

    private static final int EUICC_PACKAGE_RESULT = 81;
    private static final int BRANCH_RESULT_SIGNED = 0;
    private static final int BRANCH_ERROR_SIGNED = 1;
    private static final int BRANCH_ERROR_UNSIGNED = 2;

    private static final int FIELD_EIM_ID = 0;
    private static final int FIELD_COUNTER = 1;
    private static final int FIELD_TRANSACTION_ID = 2;
    private static final int FIELD_SEQ_NUMBER = 3;

    private static final int RESULT_LIST_PROFILE_INFO = 45;
    private static final int PROFILE_INFO_LIST_OK = 0;

    // ProfileInfo field tags (SGP.22)
    private static final int TAG_ICCID = 26;        // [APPLICATION 26] '5A'
    private static final int TAG_PROFILE_STATE = 112; // [112] '9F70'
    private static final int TAG_SPN = 17;          // [17] '91'
    private static final int TAG_PROFILE_NAME = 18;  // [18] '92'

    /**
     * @param sequenceNumber the result's {@code seqNumber} (present for signed results) — echoed
     *        back as an {@code eimAcknowledgement} so the eUICC deletes it and stops re-delivering.
     */
    public record Decoded(Long operationId, boolean success, Integer sequenceNumber,
                          Map<String, Object> details) {}

    public Decoded decode(byte[] eimPackageResult) {
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            ASN1Primitive top = ASN1Primitive.fromByteArray(eimPackageResult);
            ASN1TaggedObject euiccPackageResult = locateEuiccPackageResult(top);
            if (euiccPackageResult == null) {
                details.put("raw", Hex.toHexString(eimPackageResult));
                return new Decoded(null, false, null, details);
            }

            // [81] CHOICE is EXPLICIT -> the chosen alternative ([0]/[1]/[2]).
            ASN1TaggedObject branch = (ASN1TaggedObject) euiccPackageResult.getExplicitBaseObject();
            return switch (branch.getTagNo()) {
                case BRANCH_RESULT_SIGNED -> decodeSigned(branch, details);
                case BRANCH_ERROR_SIGNED, BRANCH_ERROR_UNSIGNED -> decodeError(branch, details);
                default -> {
                    details.put("raw", Hex.toHexString(eimPackageResult));
                    yield new Decoded(null, false, null, details);
                }
            };
        } catch (Exception ex) {
            details.put("decodeError", ex.getMessage());
            details.put("raw", Hex.toHexString(eimPackageResult));
            return new Decoded(null, false, null, details);
        }
    }

    private Decoded decodeSigned(ASN1TaggedObject branch, Map<String, Object> details) {
        ASN1Sequence resultSigned = ASN1Sequence.getInstance(branch, false);
        ASN1Sequence dataSigned = ASN1Sequence.getInstance(resultSigned.getObjectAt(0));

        Long operationId = null;
        Integer seqNumber = null;
        ASN1Sequence euiccResult = null;
        for (ASN1Encodable element : dataSigned) {
            if (!(element instanceof ASN1TaggedObject tagged)
                    || tagged.getTagClass() != BERTags.CONTEXT_SPECIFIC) {
                continue; // shouldn't happen — handled below via the untagged euiccResult
            }
            switch (tagged.getTagNo()) {
                case FIELD_EIM_ID -> details.put("eimId", utf8(tagged));
                case FIELD_COUNTER -> details.put("counterValue", integer(tagged));
                case FIELD_TRANSACTION_ID -> operationId = transactionIdToLong(octets(tagged));
                case FIELD_SEQ_NUMBER -> {
                    seqNumber = integer(tagged);
                    details.put("seqNumber", seqNumber);
                }
                default -> { /* ignore unknown */ }
            }
        }
        // euiccResult is the lone untagged (universal SEQUENCE) element.
        for (ASN1Encodable element : dataSigned) {
            if (element.toASN1Primitive() instanceof ASN1Sequence seq) {
                euiccResult = seq;
            }
        }

        if (euiccResult != null) {
            details.put("results", decodeResultList(euiccResult));
        }
        return new Decoded(operationId, true, seqNumber, details);
    }

    private List<Object> decodeResultList(ASN1Sequence euiccResult) {
        List<Object> results = new ArrayList<>();
        for (ASN1Encodable e : euiccResult) {
            if (e instanceof ASN1TaggedObject rd && rd.getTagNo() == RESULT_LIST_PROFILE_INFO) {
                results.add(decodeListProfileInfo(rd));
            } else {
                results.add(Map.of("raw", hex(e)));
            }
        }
        return results;
    }

    private Map<String, Object> decodeListProfileInfo(ASN1TaggedObject listProfileInfoResult) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "listProfileInfo");
        try {
            // [45] CHOICE EXPLICIT -> profileInfoListOk [0] SEQUENCE OF ProfileInfo | profileInfoListError [1]
            ASN1TaggedObject choice = (ASN1TaggedObject) listProfileInfoResult.getExplicitBaseObject();
            if (choice.getTagNo() == PROFILE_INFO_LIST_OK) {
                ASN1Sequence profiles = ASN1Sequence.getInstance(choice, false);
                List<Object> list = new ArrayList<>();
                for (ASN1Encodable p : profiles) {
                    list.add(decodeProfileInfo(p));
                }
                out.put("profiles", list);
            } else {
                out.put("profileInfoListError", integer(choice));
            }
        } catch (Exception ex) {
            out.put("parseError", ex.getMessage());
            out.put("raw", hex(listProfileInfoResult));
        }
        return out;
    }

    private Map<String, Object> decodeProfileInfo(ASN1Encodable profileInfo) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            ASN1Sequence fields = ASN1Sequence.getInstance(
                    ((ASN1TaggedObject) profileInfo).getBaseUniversal(false, BERTags.SEQUENCE));
            for (ASN1Encodable f : fields) {
                if (!(f instanceof ASN1TaggedObject t)) {
                    continue;
                }
                if (t.getTagClass() == BERTags.APPLICATION && t.getTagNo() == TAG_ICCID) {
                    out.put("iccid", Hex.toHexString(octets(t)));
                } else if (t.getTagClass() == BERTags.CONTEXT_SPECIFIC) {
                    switch (t.getTagNo()) {
                        case TAG_PROFILE_STATE -> out.put("state",
                                integer(t) == 1 ? "enabled" : "disabled");
                        case TAG_SPN -> out.put("serviceProviderName", utf8(t));
                        case TAG_PROFILE_NAME -> out.put("profileName", utf8(t));
                        default -> { /* ignore other ProfileInfo fields */ }
                    }
                }
            }
        } catch (Exception ex) {
            out.put("parseError", ex.getMessage());
            out.put("raw", hex(profileInfo));
        }
        return out;
    }

    private Decoded decodeError(ASN1TaggedObject branch, Map<String, Object> details) {
        details.put("euiccPackageError", branch.getTagNo() == BRANCH_ERROR_SIGNED ? "signed" : "unsigned");
        Long operationId = null;
        ASN1Sequence seq = ASN1Sequence.getInstance(branch, false);
        // For errorSigned the data is element [0]; for errorUnsigned it's the sequence itself.
        ASN1Sequence data = (branch.getTagNo() == BRANCH_ERROR_SIGNED)
                ? ASN1Sequence.getInstance(seq.getObjectAt(0)) : seq;
        for (ASN1Encodable element : data) {
            if (element instanceof ASN1TaggedObject t && t.getTagClass() == BERTags.CONTEXT_SPECIFIC) {
                switch (t.getTagNo()) {
                    case FIELD_EIM_ID -> details.put("eimId", utf8(t));
                    case FIELD_COUNTER -> details.put("counterValue", integer(t));
                    case FIELD_TRANSACTION_ID -> operationId = transactionIdToLong(octets(t));
                    default -> { /* euiccPackageErrorCode / associationToken */ }
                }
            } else if (element instanceof ASN1Integer i) {
                details.put("euiccPackageErrorCode", i.getValue().intValue());
            }
        }
        return new Decoded(operationId, false, null, details);
    }

    private static ASN1TaggedObject locateEuiccPackageResult(ASN1Primitive top) {
        if (top instanceof ASN1TaggedObject t
                && t.getTagClass() == BERTags.CONTEXT_SPECIFIC
                && t.getTagNo() == EUICC_PACKAGE_RESULT) {
            return t; // euiccPackageResult [81]
        }
        if (top instanceof ASN1Sequence seq && seq.size() > 0) {
            // ePRAndNotifications: euiccPackageResult [81] is the first element.
            ASN1Encodable first = seq.getObjectAt(0);
            if (first instanceof ASN1TaggedObject t && t.getTagNo() == EUICC_PACKAGE_RESULT) {
                return t;
            }
        }
        return null;
    }

    private static long transactionIdToLong(byte[] bytes) {
        long v = 0;
        for (byte b : bytes) {
            v = (v << 8) | (b & 0xFF);
        }
        return v;
    }

    private static String utf8(ASN1TaggedObject t) {
        return ((ASN1String) t.getBaseUniversal(false, BERTags.UTF8_STRING)).getString();
    }

    private static int integer(ASN1TaggedObject t) {
        return ((ASN1Integer) t.getBaseUniversal(false, BERTags.INTEGER)).getValue().intValue();
    }

    private static byte[] octets(ASN1TaggedObject t) {
        return ((ASN1OctetString) t.getBaseUniversal(false, BERTags.OCTET_STRING)).getOctets();
    }

    private static String hex(ASN1Encodable e) {
        try {
            return Hex.toHexString(e.toASN1Primitive().getEncoded("DER"));
        } catch (IOException ex) {
            return "<unencodable>";
        }
    }
}