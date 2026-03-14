package cz.jce.spotprice.dto.api;

import java.math.BigDecimal;

public record TodayStatsDto(
        BigDecimal minEur,
        BigDecimal minCzk,
        BigDecimal maxEur,
        BigDecimal maxCzk
) {
}
