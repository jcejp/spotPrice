package cz.jce.spotprice.dto.awattar;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AwattarDataPoint(
        @JsonProperty("start_timestamp") long startTimestamp,
        @JsonProperty("end_timestamp") long endTimestamp,
        double marketprice,
        String unit
) {
}
