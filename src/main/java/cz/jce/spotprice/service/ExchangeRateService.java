package cz.jce.spotprice.service;

import cz.jce.spotprice.dto.frankfurter.FrankfurterResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final RestClient restClient;

    @Value("${app.frankfurter.url}")
    private String frankfurterUrl;

    public BigDecimal fetchEurToCzk() {
        log.debug("Fetching EUR/CZK exchange rate from {}", frankfurterUrl);
        try {
            FrankfurterResponse response = restClient.get()
                    .uri(frankfurterUrl)
                    .retrieve()
                    .body(FrankfurterResponse.class);

            if (response == null || response.rates() == null || !response.rates().containsKey("CZK")) {
                throw new ExchangeRateFetchException("Invalid response from Frankfurter API — CZK rate missing");
            }

            BigDecimal rate = response.rates().get("CZK");
            log.debug("Fetched EUR/CZK rate: {}", rate);
            return rate;
        } catch (RestClientException e) {
            throw new ExchangeRateFetchException("Failed to fetch EUR/CZK exchange rate: " + e.getMessage(), e);
        }
    }

    public static class ExchangeRateFetchException extends RuntimeException {
        public ExchangeRateFetchException(String message) {
            super(message);
        }

        public ExchangeRateFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
