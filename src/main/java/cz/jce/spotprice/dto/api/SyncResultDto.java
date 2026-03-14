package cz.jce.spotprice.dto.api;

import java.time.Instant;

public record SyncResultDto(
        Status status,
        int recordsProcessed,
        Instant syncedAt,
        String message
) {
    public enum Status { SYNCED, SKIPPED, FAILED }
}
