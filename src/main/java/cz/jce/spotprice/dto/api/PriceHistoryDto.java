package cz.jce.spotprice.dto.api;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceHistoryDto(
        Instant timestamp,
        BigDecimal priceEur,
        BigDecimal priceCzk
) {
}
