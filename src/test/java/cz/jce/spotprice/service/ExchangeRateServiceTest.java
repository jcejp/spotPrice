package cz.jce.spotprice.service;

import cz.jce.spotprice.dto.frankfurter.FrankfurterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock private RestClient restClient;
    @Mock private RestClient.ResponseSpec responseSpec;

    private final RestClient.RequestHeadersUriSpec<?> getSpec = mock(RestClient.RequestHeadersUriSpec.class);

    private ExchangeRateService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeRateService(restClient);
        ReflectionTestUtils.setField(service, "frankfurterUrl", "http://test-frankfurter/");

        doReturn(getSpec).when(restClient).get();
        doReturn(getSpec).when(getSpec).uri(anyString());
        doReturn(responseSpec).when(getSpec).retrieve();
    }

    @Test
    void fetchEurToCzk_validResponse_returnsRate() {
        var response = new FrankfurterResponse(
                BigDecimal.ONE, "EUR", "2024-01-15",
                Map.of("CZK", new BigDecimal("24.56"), "USD", new BigDecimal("1.09")));
        when(responseSpec.body(FrankfurterResponse.class)).thenReturn(response);

        BigDecimal rate = service.fetchEurToCzk();

        assertThat(rate).isEqualByComparingTo("24.56");
    }

    @Test
    void fetchEurToCzk_nullResponse_throwsExchangeRateFetchException() {
        when(responseSpec.body(FrankfurterResponse.class)).thenReturn(null);

        assertThatThrownBy(() -> service.fetchEurToCzk())
                .isInstanceOf(ExchangeRateService.ExchangeRateFetchException.class)
                .hasMessageContaining("CZK rate missing");
    }

    @Test
    void fetchEurToCzk_nullRatesMap_throwsExchangeRateFetchException() {
        var response = new FrankfurterResponse(BigDecimal.ONE, "EUR", "2024-01-15", null);
        when(responseSpec.body(FrankfurterResponse.class)).thenReturn(response);

        assertThatThrownBy(() -> service.fetchEurToCzk())
                .isInstanceOf(ExchangeRateService.ExchangeRateFetchException.class)
                .hasMessageContaining("CZK rate missing");
    }

    @Test
    void fetchEurToCzk_czkKeyMissing_throwsExchangeRateFetchException() {
        var response = new FrankfurterResponse(
                BigDecimal.ONE, "EUR", "2024-01-15",
                Map.of("USD", new BigDecimal("1.09")));
        when(responseSpec.body(FrankfurterResponse.class)).thenReturn(response);

        assertThatThrownBy(() -> service.fetchEurToCzk())
                .isInstanceOf(ExchangeRateService.ExchangeRateFetchException.class)
                .hasMessageContaining("CZK rate missing");
    }

    @Test
    void fetchEurToCzk_restClientException_wrapsInExchangeRateFetchException() {
        doThrow(new RestClientException("connection timeout")).when(getSpec).retrieve();

        assertThatThrownBy(() -> service.fetchEurToCzk())
                .isInstanceOf(ExchangeRateService.ExchangeRateFetchException.class)
                .hasMessageContaining("Failed to fetch EUR/CZK")
                .hasCauseInstanceOf(RestClientException.class);
    }
}
