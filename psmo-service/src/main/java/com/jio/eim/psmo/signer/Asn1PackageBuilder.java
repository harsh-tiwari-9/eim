package com.jio.eim.psmo.signer;

import com.jio.eim.psmo.dto.PsmoCommandMessage;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERGeneralizedTime;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERUTF8String;
import org.springframework.stereotype.Component;

@Component
public class Asn1PackageBuilder implements PackageBuilder {

    private static final String CI_REFERENCE_LAB = "LAB";
    private static final long PACKAGE_TTL_MINUTES = 60;

    @Override
    public BuiltPackage build(PsmoCommandMessage message) {
        try {
            Instant notBefore = Instant.now();
            Instant notAfter = notBefore.plus(PACKAGE_TTL_MINUTES, ChronoUnit.MINUTES);

            ASN1EncodableVector headerVec = new ASN1EncodableVector();
            headerVec.add(new ASN1Integer(message.operationId()));
            headerVec.add(new DEROctetString(hexToBytes(message.eid())));
            headerVec.add(new DERGeneralizedTime(Date.from(notBefore)));
            headerVec.add(new DERGeneralizedTime(Date.from(notAfter)));
            headerVec.add(new ASN1Integer(BigInteger.valueOf(message.operationId())));
            headerVec.add(new DERUTF8String(CI_REFERENCE_LAB));
            ASN1Encodable header = new DERSequence(headerVec);

            ASN1Encodable command = buildCommand(message);

            ASN1EncodableVector tbsVec = new ASN1EncodableVector();
            tbsVec.add(header);
            tbsVec.add(command);
            byte[] tbs = new DERSequence(tbsVec).getEncoded("DER");

            return new BuiltPackage(header, command, tbs);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to DER-encode EimPackage", ex);
        }
    }

    @Override
    public byte[] attachSignature(BuiltPackage built, byte[] signature) {
        try {
            ASN1EncodableVector outer = new ASN1EncodableVector();
            outer.add(built.header());
            outer.add(built.command());
            outer.add(new DEROctetString(signature == null ? new byte[0] : signature));
            return new DERSequence(outer).getEncoded("DER");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to attach signature", ex);
        }
    }

    private ASN1Encodable buildCommand(PsmoCommandMessage message) {
        return switch (message.type()) {
            case "AUDIT" ->
                // PsmoCommand CHOICE [3] ListProfileInfoRequest ::= SEQUENCE { }
                    new DERTaggedObject(true, 3, new DERSequence());
            default -> throw new IllegalArgumentException(
                    "Unsupported PSMO type for ASN.1 encoder: " + message.type());
        };
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