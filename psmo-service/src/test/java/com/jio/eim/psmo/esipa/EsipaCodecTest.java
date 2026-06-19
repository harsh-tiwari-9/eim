package com.jio.eim.psmo.esipa;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jio.eim.psmo.esipa.EsipaCodec.DecodedRequest;
import com.jio.eim.psmo.esipa.EsipaCodec.Function;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.BERTags;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.junit.jupiter.api.Test;

class EsipaCodecTest {

    private final EsipaCodec codec = new EsipaCodec();

    private static final int TAG_GET_EIM_PACKAGE = 79;
    private static final int TAG_PROVIDE_RESULT = 80;

    private static byte[] eid16() {
        byte[] eid = new byte[16];
        for (int i = 0; i < 16; i++) {
            eid[i] = (byte) (0x89 + i);
        }
        return eid;
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02X", x));
        }
        return sb.toString();
    }

    /** Build a GetEimPackageRequest exactly as the IPA would: [79] SEQUENCE { eidValue [APP 26] }. */
    private static byte[] getEimPackageRequest(byte[] eid) throws Exception {
        var eidValue = new DERTaggedObject(false, BERTags.APPLICATION, 26, new DEROctetString(eid));
        return new DERTaggedObject(false, BERTags.CONTEXT_SPECIFIC, TAG_GET_EIM_PACKAGE,
                new DERSequence(eidValue)).getEncoded("DER");
    }

    @Test
    void decodesGetEimPackageRequestAndExtractsEid() throws Exception {
        byte[] eid = eid16();
        DecodedRequest req = codec.decode(getEimPackageRequest(eid));

        assertEquals(Function.GET_EIM_PACKAGE, req.function());
        assertEquals(hex(eid), req.eidHex());
    }

    @Test
    void encodesNoPackageAvailableAsExplicit79WrappedInteger() throws Exception {
        byte[] der = codec.encodeGetEimPackageError(EsipaCodec.ERR_NO_PACKAGE_AVAILABLE);

        // Outer selector is context [79] -> first bytes 'BF 4F'
        assertEquals((byte) 0xBF, der[0]);
        assertEquals((byte) 0x4F, der[1]);

        ASN1Primitive top = ASN1Primitive.fromByteArray(der);
        assertTrue(top instanceof ASN1TaggedObject);
        ASN1TaggedObject t = (ASN1TaggedObject) top;
        assertEquals(BERTags.CONTEXT_SPECIFIC, t.getTagClass());
        assertEquals(TAG_GET_EIM_PACKAGE, t.getTagNo());
        ASN1Integer code = ASN1Integer.getInstance(t.getBaseObject().toASN1Primitive());
        assertEquals(EsipaCodec.ERR_NO_PACKAGE_AVAILABLE, code.intValueExact());
    }

    @Test
    void wrapsEuiccPackageRequestInGetEimPackageResponse() throws Exception {
        // Stand-in EuiccPackageRequest [81] payload
        byte[] euiccPackageRequest = new DERTaggedObject(false, BERTags.CONTEXT_SPECIFIC, 81,
                new DERSequence()).getEncoded("DER");

        byte[] der = codec.encodeGetEimPackageResponse(euiccPackageRequest);

        ASN1TaggedObject top = (ASN1TaggedObject) ASN1Primitive.fromByteArray(der);
        assertEquals(TAG_GET_EIM_PACKAGE, top.getTagNo());
        ASN1TaggedObject inner = (ASN1TaggedObject) top.getBaseObject().toASN1Primitive();
        assertEquals(81, inner.getTagNo());
    }

    @Test
    void encodesProvideResultAckAsExplicit80EmptySequence() throws Exception {
        byte[] der = codec.encodeProvideResultAck();

        ASN1TaggedObject top = (ASN1TaggedObject) ASN1Primitive.fromByteArray(der);
        assertEquals(TAG_PROVIDE_RESULT, top.getTagNo());
        ASN1Sequence empty = ASN1Sequence.getInstance(top.getBaseObject().toASN1Primitive());
        assertEquals(0, empty.size());
    }

    @Test
    void roundTripsEidThroughByteScanRegardlessOfSurroundingFields() throws Exception {
        // provideEimPackageResult [80] SEQUENCE { eidValue [APP 26] ... } — eid must still be found
        byte[] eid = eid16();
        var eidValue = new DERTaggedObject(false, BERTags.APPLICATION, 26, new DEROctetString(eid));
        byte[] der = new DERTaggedObject(false, BERTags.CONTEXT_SPECIFIC, TAG_PROVIDE_RESULT,
                new DERSequence(eidValue)).getEncoded("DER");

        DecodedRequest req = codec.decode(der);
        assertEquals(Function.PROVIDE_EIM_PACKAGE_RESULT, req.function());
        assertArrayEquals(eid, hexToBytes(req.eidHex()));
    }

    private static byte[] hexToBytes(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}