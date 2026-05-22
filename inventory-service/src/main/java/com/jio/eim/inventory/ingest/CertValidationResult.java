package com.jio.eim.inventory.ingest;

import com.jio.eim.inventory.dto.InventoryResponse.CertSummary;

public record CertValidationResult(CertSummary certSummary, String errorMessage) {

    public boolean isSuccess() {
        return errorMessage == null && certSummary != null;
    }

    public static CertValidationResult ok(CertSummary certSummary) {
        return new CertValidationResult(certSummary, null);
    }

    public static CertValidationResult fail(String errorMessage) {
        return new CertValidationResult(null, errorMessage);
    }
}
