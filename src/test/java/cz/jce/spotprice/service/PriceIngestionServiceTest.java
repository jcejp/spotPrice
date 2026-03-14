package cz.jce.spotprice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.jce.spotprice.dto.api.SyncResultDto;
import cz.jce.spotprice.entity.SyncLog;
import cz.jce.spotprice.repository.SyncLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceIngestionServiceTest {

    private static final String VALID_JSON = """
            {"object":"array","data":[
                {"start_timestamp":1700000000000,"end_timestamp":1700003600000,"marketprice":45.5,"unit":"Eur/MWh"}
            ]}""";

    @Mock
    private RestClient restClient;
    @Mock
    private SyncLogRepository syncLogRepository;
    @Mock
    private ExchangeRateService exchangeRateService;
    @Mock
    private PricePersistenceService pricePersistenceService;
    @Mock
    private RestClient.ResponseSpec responseSpec;

    private final RestClient.RequestHeadersUriSpec<?> getSpec = mock(RestClient.RequestHeadersUriSpec.class);

    private PriceIngestionService service;

    @BeforeEach
    void setUp() {
        service = new PriceIngestionService(
                restClient, new ObjectMapper(), syncLogRepository, exchangeRateService, pricePersistenceService);
        ReflectionTestUtils.setField(service, "awattarUrl", "http://test-awattar/");

        // lenient: tests that stub doThrow on restClient.get() never reach the chain
        lenient().doReturn(getSpec).when(restClient).get();
        lenient().doReturn(getSpec).when(getSpec).uri(anyString());
        lenient().doReturn(responseSpec).when(getSpec).retrieve();
    }

    // ── triggerManualSync ────────────────────────────────────────────────────

    @Test
    void triggerManualSync_validResponse_returnsSyncedResult() {
        doReturn(VALID_JSON).when(responseSpec).body(String.class);
        when(exchangeRateService.fetchEurToCzk()).thenReturn(new BigDecimal("24.5"));
        when(pricePersistenceService.persist(any(), any(), anyString())).thenReturn(1);

        SyncResultDto result = service.triggerManualSync();

        assertThat(result.status()).isEqualTo(SyncResultDto.Status.SYNCED);
        assertThat(result.recordsProcessed()).isEqualTo(1);
        assertThat(result.message()).contains("1");
    }

    @Test
    void triggerManualSync_bypassesHashCheck_alwaysPersists() {
        doReturn(VALID_JSON).when(responseSpec).body(String.class);
        when(exchangeRateService.fetchEurToCzk()).thenReturn(new BigDecimal("24.5"));
        when(pricePersistenceService.persist(any(), any(), anyString())).thenReturn(1);

        SyncResultDto result = service.triggerManualSync();

        // force=true skips the hash check — syncLogRepository must not be consulted
        assertThat(result.status()).isEqualTo(SyncResultDto.Status.SYNCED);
        verify(pricePersistenceService).persist(any(), any(), anyString());
        verifyNoInteractions(syncLogRepository);
    }

    @Test
    void triggerManualSync_passesComputedHashToPersistence() {
        doReturn(VALID_JSON).when(responseSpec).body(String.class);
        when(exchangeRateService.fetchEurToCzk()).thenReturn(new BigDecimal("24.5"));
        when(pricePersistenceService.persist(any(), any(), anyString())).thenReturn(1);

        service.triggerManualSync();

        verify(pricePersistenceService).persist(any(), any(), eq(sha256(VALID_JSON)));
    }

    @Test
    void triggerManualSync_passesExchangeRateToPersistence() {
        doReturn(VALID_JSON).when(responseSpec).body(String.class);
        BigDecimal rate = new BigDecimal("24.567");
        when(exchangeRateService.fetchEurToCzk()).thenReturn(rate);
        when(pricePersistenceService.persist(any(), any(), anyString())).thenReturn(1);

        service.triggerManualSync();

        verify(pricePersistenceService).persist(any(), eq(rate), anyString());
    }

    @Test
    void triggerManualSync_nullApiResponse_throwsRestClientException() {
        doReturn(null).when(responseSpec).body(String.class);

        assertThatThrownBy(() -> service.triggerManualSync())
                .isInstanceOf(RestClientException.class);
        verifyNoInteractions(pricePersistenceService);
    }

    @Test
    void triggerManualSync_invalidJson_throwsIllegalStateException() {
        doReturn("{invalid-json}").when(responseSpec).body(String.class);
        when(exchangeRateService.fetchEurToCzk()).thenReturn(new BigDecimal("24.5"));

        assertThatThrownBy(() -> service.triggerManualSync())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse aWATTar response");
    }

    // ── syncPrices (scheduled, force=false) ───────────────────────────────────

    @Test
    void syncPrices_hashUnchanged_skipsSync() {
        doReturn(VALID_JSON).when(responseSpec).body(String.class);
        when(syncLogRepository.findTopByOrderBySyncTimeDesc())
                .thenReturn(Optional.of(new SyncLog(Instant.now(), sha256(VALID_JSON))));

        service.syncPrices();

        verifyNoInteractions(exchangeRateService, pricePersistenceService);
    }

    @Test
    void syncPrices_hashChanged_callsPersist() {
        doReturn(VALID_JSON).when(responseSpec).body(String.class);
        when(syncLogRepository.findTopByOrderBySyncTimeDesc())
                .thenReturn(Optional.of(new SyncLog(Instant.now(), "old-different-hash")));
        when(exchangeRateService.fetchEurToCzk()).thenReturn(new BigDecimal("24.5"));
        when(pricePersistenceService.persist(any(), any(), anyString())).thenReturn(1);

        service.syncPrices();

        verify(pricePersistenceService).persist(any(), any(), anyString());
    }

    @Test
    void syncPrices_noExistingLog_callsPersist() {
        doReturn(VALID_JSON).when(responseSpec).body(String.class);
        when(syncLogRepository.findTopByOrderBySyncTimeDesc()).thenReturn(Optional.empty());
        when(exchangeRateService.fetchEurToCzk()).thenReturn(new BigDecimal("24.5"));
        when(pricePersistenceService.persist(any(), any(), anyString())).thenReturn(1);

        service.syncPrices();

        verify(pricePersistenceService).persist(any(), any(), anyString());
    }

    @Test
    void syncPrices_exceptionDuringFetch_doesNotPropagate() {
        doThrow(new RestClientException("connection refused")).when(restClient).get();

        // Direct call — if the exception propagates the test framework fails the test
        service.syncPrices();

        verifyNoInteractions(exchangeRateService, pricePersistenceService);
    }

    @Test
    void syncPrices_exchangeRateFailure_doesNotPropagate() {
        doReturn(VALID_JSON).when(responseSpec).body(String.class);
        when(syncLogRepository.findTopByOrderBySyncTimeDesc()).thenReturn(Optional.empty());
        when(exchangeRateService.fetchEurToCzk())
                .thenThrow(new ExchangeRateService.ExchangeRateFetchException("API down"));

        service.syncPrices();

        verifyNoInteractions(pricePersistenceService);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
