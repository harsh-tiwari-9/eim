package com.jio.eim.inventory.ingest;

import com.jio.eim.inventory.dto.InventoryRequest;

public record IngestRecordMessage(long jobId, long rowId, String eid, InventoryRequest payload) {}
