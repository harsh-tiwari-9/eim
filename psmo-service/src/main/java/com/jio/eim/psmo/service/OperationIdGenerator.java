package com.jio.eim.psmo.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Generates the numeric operation id: {@code yyyyMMddHHmmss} (UTC) followed by a 4-digit per-second
 * counter — e.g. {@code 202607230912000001} (18 digits). It stays a {@code Long} so it also serves
 * as the on-card {@code eimTransactionId} (encoded as an OCTET STRING, well within the 16-byte
 * limit). The counter resets each second; up to 10 000 ids per second are supported, far above the
 * rate of PSMO operations.
 */
@Component
public class OperationIdGenerator {

    private static final DateTimeFormatter SECOND = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int COUNTER_LIMIT = 10_000;  // 4 digits

    private long lastSecond = -1L;
    private int counter = 0;

    /** Returns the next unique numeric operation id. Thread-safe. */
    public synchronized long next() {
        long second = Long.parseLong(LocalDateTime.now(ZoneOffset.UTC).format(SECOND));
        if (second != lastSecond) {
            lastSecond = second;
            counter = 0;
        }
        if (counter >= COUNTER_LIMIT) {
            // >10 000 ids in one second — spin to the next second (practically never happens).
            try {
                Thread.sleep(1);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return next();
        }
        return second * COUNTER_LIMIT + counter++;
    }
}
