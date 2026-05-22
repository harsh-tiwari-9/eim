package com.jio.eim.inventory.ingest;

import com.jio.eim.inventory.dto.InventoryResponse.CertSummary;

public record RegisterResult(CertSummary certSummary, String remarks) {}
