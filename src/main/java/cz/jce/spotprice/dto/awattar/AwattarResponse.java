package cz.jce.spotprice.dto.awattar;

import java.util.List;

public record AwattarResponse(
        String object,
        List<AwattarDataPoint> data
) {
}
