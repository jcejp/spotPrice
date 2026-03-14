package cz.jce.spotprice.controller;

import cz.jce.spotprice.dto.api.CurrentPriceDto;
import cz.jce.spotprice.dto.api.PriceHistoryDto;
import cz.jce.spotprice.dto.api.TodayStatsDto;
import cz.jce.spotprice.service.PriceQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PriceController.class)
class PriceControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    PriceQueryService priceQueryService;

    private static final Instant NOW_HOUR = Instant.now().truncatedTo(ChronoUnit.HOURS);

    // ── GET /api/prices/current ───────────────────────────────────────────────

    @Test
    void getCurrent_dataExists_returns200WithPrices() throws Exception {
        var dto = new CurrentPriceDto(NOW_HOUR, new BigDecimal("50.123456"), new BigDecimal("1228.024177"));
        when(priceQueryService.getCurrentPrice()).thenReturn(dto);

        mockMvc.perform(get("/api/prices/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceEur").value(50.123456))
                .andExpect(jsonPath("$.priceCzk").value(1228.024177))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getCurrent_noData_returns404WithErrorMessage() throws Exception {
        when(priceQueryService.getCurrentPrice())
                .thenThrow(new NoSuchElementException("No price data available for current hour"));

        mockMvc.perform(get("/api/prices/current"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No price data available for current hour"));
    }

    // ── GET /api/prices/today-stats ───────────────────────────────────────────

    @Test
    void getTodayStats_dataExists_returns200WithStats() throws Exception {
        var dto = new TodayStatsDto(
                new BigDecimal("30.0"), new BigDecimal("735.0"),
                new BigDecimal("70.0"), new BigDecimal("1715.0"));
        when(priceQueryService.getTodayStats()).thenReturn(dto);

        mockMvc.perform(get("/api/prices/today-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minEur").value(30.0))
                .andExpect(jsonPath("$.maxEur").value(70.0))
                .andExpect(jsonPath("$.minCzk").value(735.0))
                .andExpect(jsonPath("$.maxCzk").value(1715.0));
    }

    @Test
    void getTodayStats_noData_returns404() throws Exception {
        when(priceQueryService.getTodayStats())
                .thenThrow(new NoSuchElementException("No price data available for today"));

        mockMvc.perform(get("/api/prices/today-stats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No price data available for today"));
    }

    // ── GET /api/prices/history ───────────────────────────────────────────────

    @Test
    void getHistory_noParams_returns200WithList() throws Exception {
        var dto = new PriceHistoryDto(NOW_HOUR, new BigDecimal("50.0"), new BigDecimal("1225.0"));
        when(priceQueryService.getHistory(any(), any())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/prices/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].priceEur").value(50.0))
                .andExpect(jsonPath("$[0].priceCzk").value(1225.0));
    }

    @Test
    void getHistory_withValidDateRange_returns200() throws Exception {
        when(priceQueryService.getHistory(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/prices/history?startDate=2024-01-10&endDate=2024-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getHistory_noDataInRange_returns200WithEmptyArray() throws Exception {
        when(priceQueryService.getHistory(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/prices/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getHistory_startDateWithoutEndDate_returns200() throws Exception {
        when(priceQueryService.getHistory(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/prices/history?startDate=2024-01-10"))
                .andExpect(status().isOk());
    }
}
