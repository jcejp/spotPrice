package cz.jce.spotprice.controller;

import cz.jce.spotprice.dto.api.SyncResultDto;
import cz.jce.spotprice.service.ExchangeRateService;
import cz.jce.spotprice.service.PriceIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientException;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SyncController.class)
class SyncControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PriceIngestionService priceIngestionService;

    @Test
    void triggerSync_success_returns200WithSyncedStatus() throws Exception {
        var result = new SyncResultDto(
                SyncResultDto.Status.SYNCED, 24, Instant.now(), "Successfully synced 24 records");
        when(priceIngestionService.triggerManualSync()).thenReturn(result);

        mockMvc.perform(post("/api/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SYNCED"))
                .andExpect(jsonPath("$.recordsProcessed").value(24))
                .andExpect(jsonPath("$.message").value(containsString("24")));
    }

    @Test
    void triggerSync_exchangeRateUnavailable_returns503WithFailedStatus() throws Exception {
        when(priceIngestionService.triggerManualSync())
                .thenThrow(new ExchangeRateService.ExchangeRateFetchException("API down"));

        mockMvc.perform(post("/api/sync"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.recordsProcessed").value(0))
                .andExpect(jsonPath("$.message").value(containsString("Exchange rate service unavailable")));
    }

    @Test
    void triggerSync_awattarUnavailable_returns503WithFailedStatus() throws Exception {
        when(priceIngestionService.triggerManualSync())
                .thenThrow(new RestClientException("connection refused"));

        mockMvc.perform(post("/api/sync"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.recordsProcessed").value(0))
                .andExpect(jsonPath("$.message").value(containsString("aWATTar API unavailable")));
    }
}
