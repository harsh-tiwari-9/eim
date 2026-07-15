package com.jio.eim.psmo.signer;

import com.jio.eim.psmo.dto.PsmoCommandMessage;
import com.jio.eim.psmo.entity.EimPackageCounter;
import com.jio.eim.psmo.repository.EimPackageCounterRepository;
import com.jio.eim.psmo.util.IccidCodec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.BERTags;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERUTF8String;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds a spec-compliant SGP.32 {@code EuiccPackageRequest} (§2.11.1, tag 'BF51') for a PSMO.
 *
 * <pre>
 * EuiccPackageRequest ::= [81] SEQUENCE { euiccPackageSigned, eimSignature [APPLICATION 55] }
 * EuiccPackageSigned  ::= SEQUENCE { eimId [0], eidValue [APPLICATION 26], counterValue [1],
 *                                    eimTransactionId [2] OPTIONAL, euiccPackage }
 * EuiccPackage        ::= CHOICE { psmoList SEQUENCE OF Psmo, ecoList SEQUENCE OF Eco } -- AUTOMATIC: psmoList=[0]
 * Psmo.listProfileInfo ::= [45] ProfileInfoListRequest -- AUDIT, tag 'BF2D'
 * </pre>
 *
 * The eIM signs {@code DER(euiccPackageSigned)} concatenated with the {@code associationToken}
 * data object (value 0 = '84 01 00' when no association token is configured) — see §2.11.1.
 */
@Component
public class Asn1PackageBuilder implements PackageBuilder {

    // associationToken data object with value 0: [4] INTEGER 0  ->  '84 01 00' (no token configured).
    private static final byte[] ASSOCIATION_TOKEN_ZERO = {(byte) 0x84, 0x01, 0x00};

    private final String eimId;
    private final EimPackageCounterRepository counterRepository;

    public Asn1PackageBuilder(
            @Value("${eim.psmo.eim-id:id1}") String eimId,
            EimPackageCounterRepository counterRepository) {
        this.eimId = eimId;
        this.counterRepository = counterRepository;
    }

    @Override
    public BuiltPackage build(PsmoCommandMessage message) {
        try {
            long counterValue = nextCounterValue(message.eid());

            // euiccPackage ::= CHOICE -> psmoList [0] SEQUENCE OF Psmo
            ASN1Encodable psmo = buildPsmo(message);
            ASN1Encodable psmoList = new DERTaggedObject(false, 0, new DERSequence(psmo));

            // euiccPackageSigned
            ASN1EncodableVector signedVec = new ASN1EncodableVector();
            signedVec.add(new DERTaggedObject(false, 0, new DERUTF8String(eimId)));            // eimId [0]
            signedVec.add(new DERTaggedObject(false, BERTags.APPLICATION, 26,
                    new DEROctetString(hexToBytes(message.eid()))));                            // eidValue [APP 26]
            signedVec.add(new DERTaggedObject(false, 1, new ASN1Integer(BigInteger.valueOf(counterValue)))); // counterValue [1]
            // eimTransactionId [2] = operationId — the eUICC echoes it in the result, linking it back.
            signedVec.add(new DERTaggedObject(false, 2, new DEROctetString(transactionId(message.operationId()))));
            signedVec.add(psmoList);                                                            // euiccPackage
            ASN1Encodable euiccPackageSigned = new DERSequence(signedVec);

            // toBeSigned = DER(euiccPackageSigned) || associationToken(0)
            ByteArrayOutputStream tbs = new ByteArrayOutputStream();
            tbs.write(euiccPackageSigned.toASN1Primitive().getEncoded("DER"));
            tbs.write(ASSOCIATION_TOKEN_ZERO);

            return new BuiltPackage(euiccPackageSigned, tbs.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to DER-encode EuiccPackageRequest", ex);
        }
    }

    @Override
    public byte[] attachSignature(BuiltPackage built, byte[] signature) {
        try {
            // eimSignature [APPLICATION 55] OCTET STRING -- Tag '5F37'
            ASN1Encodable eimSignature = new DERTaggedObject(false, BERTags.APPLICATION, 55,
                    new DEROctetString(signature == null ? new byte[0] : signature));

            ASN1EncodableVector reqVec = new ASN1EncodableVector();
            reqVec.add(built.euiccPackageSigned());
            reqVec.add(eimSignature);

            // EuiccPackageRequest ::= [81] SEQUENCE  -- Tag 'BF51'
            ASN1Encodable euiccPackageRequest = new DERTaggedObject(false, 81, new DERSequence(reqVec));
            return euiccPackageRequest.toASN1Primitive().getEncoded("DER");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to attach eimSignature", ex);
        }
    }

    /**
     * Builds a {@code ProfileDownloadTriggerRequest} ([84] / BF54), unsigned (SGP.32 §2.11.1.3):
     * <pre>
     * ProfileDownloadTriggerRequest ::= [84] SEQUENCE {
     *     profileDownloadData [0] ProfileDownloadData OPTIONAL,   -- CHOICE, so [0] is EXPLICIT
     *     eimTransactionId    [2] TransactionId OPTIONAL }
     * ProfileDownloadData ::= CHOICE { activationCode [0] UTF8String, contactDefaultSmdp [1] NULL, ... }
     * </pre>
     * A non-blank activation code selects {@code activationCode [0]}; a blank one selects
     * {@code contactDefaultSmdp [1]}. {@code eimTransactionId} is set to the operationId so the
     * download outcome can be correlated back (as for PSMOs).
     */
    @Override
    public byte[] buildProfileDownloadTrigger(PsmoCommandMessage message) {
        try {
            String ac = message.activationCode();
            ASN1Encodable profileDownloadData = (ac != null && !ac.isBlank())
                    ? new DERTaggedObject(false, 0, new DERUTF8String(ac.trim()))  // activationCode [0]
                    : new DERTaggedObject(false, 1, DERNull.INSTANCE);             // contactDefaultSmdp [1]

            ASN1EncodableVector triggerVec = new ASN1EncodableVector();
            // profileDownloadData [0] is EXPLICIT because its type is a CHOICE (X.680 auto-tagging).
            triggerVec.add(new DERTaggedObject(true, 0, profileDownloadData));
            // eimTransactionId [2] = operationId — the eUICC/IPA echoes it so we can link the result.
            triggerVec.add(new DERTaggedObject(false, 2, new DEROctetString(transactionId(message.operationId()))));

            // ProfileDownloadTriggerRequest ::= [84] SEQUENCE -- Tag 'BF54'
            ASN1Encodable trigger = new DERTaggedObject(false, 84, new DERSequence(triggerVec));
            return trigger.toASN1Primitive().getEncoded("DER");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to DER-encode ProfileDownloadTriggerRequest", ex);
        }
    }

    private ASN1Encodable buildPsmo(PsmoCommandMessage message) {
        return switch (message.type()) {
            // AUDIT -> Psmo.listProfileInfo [45] ProfileInfoListRequest (empty = default tag list), tag 'BF2D'
            case "AUDIT" -> new DERTaggedObject(false, 45, new DERSequence());
            // SGP.32 §2.11.1 Psmo CHOICE. iccid [APPLICATION 26] bears its own tag, so the module's
            // AUTOMATIC TAGS does not apply inside these SEQUENCEs — the ICCID stays a bare '5A'
            // field, matching what the eUICC returns in ProfileInfo and reuses the eidValue pattern.
            //   enable  [3] SEQUENCE { iccid [APPLICATION 26] Iccid, rollbackFlag NULL OPTIONAL }
            //   disable [4] SEQUENCE { iccid [APPLICATION 26] Iccid }
            //   delete  [5] SEQUENCE { iccid [APPLICATION 26] Iccid }
            // rollbackFlag is omitted on ENABLE (we do not request rollback semantics).
            case "ENABLE" -> new DERTaggedObject(false, 3, new DERSequence(iccidField(message)));
            case "DISABLE" -> new DERTaggedObject(false, 4, new DERSequence(iccidField(message)));
            case "DELETE" -> new DERTaggedObject(false, 5, new DERSequence(iccidField(message)));
            default -> throw new IllegalArgumentException(
                    "Unsupported PSMO type for ASN.1 encoder: " + message.type());
        };
    }

    /** iccid [APPLICATION 26] Iccid — the target profile, swapped-BCD encoded per E.118 (tag '5A'). */
    private ASN1Encodable iccidField(PsmoCommandMessage message) {
        String iccid = message.targetIccid();
        if (iccid == null || iccid.isBlank()) {
            throw new IllegalArgumentException(
                    message.type() + " requires targetIccid (operation " + message.operationId() + ")");
        }
        return new DERTaggedObject(false, BERTags.APPLICATION, 26,
                new DEROctetString(IccidCodec.toBytes(iccid)));
    }

    /**
     * Returns the next replay-protection counter for this eUICC. The eIM increments its counter by
     * 1 per eUICC Package (§2.11.1); the card stores the highest received value per Associated eIM.
     * Runs inside the caller's transaction with a row lock to stay monotonic under concurrency.
     */
    private long nextCounterValue(String eid) {
        EimPackageCounter counter = counterRepository.findForUpdate(eid)
                .orElseGet(() -> {
                    EimPackageCounter created = new EimPackageCounter();
                    created.setEid(eid);
                    created.setCounterValue(0L);
                    return created;
                });
        long next = counter.getCounterValue() + 1;
        counter.setEimId(eimId);
        counter.setCounterValue(next);
        counterRepository.save(counter);
        return next;
    }

    /** Encodes an operationId as a minimal big-endian TransactionId (OCTET STRING, 1..16 bytes). */
    static byte[] transactionId(long operationId) {
        byte[] full = new byte[8];
        long v = operationId;
        for (int i = 7; i >= 0; i--) {
            full[i] = (byte) (v & 0xFF);
            v >>>= 8;
        }
        int start = 0;
        while (start < 7 && full[start] == 0) {
            start++;
        }
        byte[] trimmed = new byte[8 - start];
        System.arraycopy(full, start, trimmed, 0, trimmed.length);
        return trimmed;
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
}