package cz.jce.spotprice.dto.frankfurter;

import java.math.BigDecimal;
import java.util.Map;

public record FrankfurterResponse(
        BigDecimal amount,
        String base,
        String date,
        Map<String, BigDecimal> rates
) {
}
