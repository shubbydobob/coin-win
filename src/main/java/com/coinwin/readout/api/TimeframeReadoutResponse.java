package com.coinwin.readout.api;

import com.coinwin.readout.domain.IndicatorReadout;
import com.coinwin.readout.domain.ZoneReadout;
import com.coinwin.readout.domain.TimeframeReadout;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * 한 주기가 지금 말하는 것.
 *
 * <p><b>위치와 값을 함께 낸다.</b> "구름 위" 만으로는 아슬아슬하게 위인지 한참 위인지 알 수
 * 없고, 그 차이가 분할 진입에서 첫 칸을 어디 둘지를 가른다.
 *
 * <p><b>방향을 말하지 않는다.</b> 여기 있는 것은 전부 관측이다. "구름 위이고 지지대에서 0.4%
 * 위" 는 사실이고 "그러니 롱" 은 예측이며, 이 저장소는 그 종류의 전제를 7년 15,110봉에서
 * 반증했다({@code docs/adr/021}).
 */
@Schema(description = "한 주기의 지표·지지저항 판독")
public record TimeframeReadoutResponse(

        @Schema(description = "캔들 주기", example = "15m",
                allowableValues = {"15m", "1h", "4h", "1d", "1w"})
        String interval,

        @Schema(description = "판독 기준이 된 봉의 시각(UTC). **아직 닫히지 않은 봉일 수 있다**",
                example = "2026-08-25T01:15:00Z")
        Instant at,

        @Schema(description = "그 봉의 종가. 아래 모든 위치 판정이 이 값 기준이다",
                example = "79256.90")
        BigDecimal close,

        @Schema(description = """
                이 시점의 변동성(ATR). **대의 폭과 손절 버퍼가 이 단위로 정해진다** —
                같은 1% 손절도 ATR 이 크면 잡음 안이고 작으면 진짜 이탈이다.""",
                example = "412.30")
        BigDecimal atr,

        @Schema(description = "구름 대비 위치", example = "ABOVE",
                allowableValues = {"ABOVE", "INSIDE", "BELOW"})
        String ichimoku,

        @Schema(description = "전환선 (9)", example = "79100.00")
        BigDecimal conversionLine,

        @Schema(description = "기준선 (26)", example = "78420.00")
        BigDecimal baseLine,

        @Schema(description = "구름 위 모서리. 두 선행스팬 중 큰 쪽이다", example = "78900.00")
        BigDecimal cloudTop,

        @Schema(description = "구름 아래 모서리", example = "77300.00")
        BigDecimal cloudBottom,

        @Schema(description = "밴드 대비 위치", example = "INSIDE",
                allowableValues = {"ABOVE", "INSIDE", "BELOW"})
        String bollinger,

        @Schema(description = "밴드 상단", example = "80120.00")
        BigDecimal bollingerUpper,

        @Schema(description = "밴드 중심. 20봉 단순이동평균이다", example = "78900.00")
        BigDecimal bollingerMiddle,

        @Schema(description = "밴드 하단", example = "77680.00")
        BigDecimal bollingerLower,

        @Schema(description = """
                밴드 폭 (%). **좁으면 변동성이 죽어 있다는 뜻**이고 그 자체로 방향을 뜻하지
                않는다 — 좁아진 뒤 어느 쪽으로 터지는가는 이 수가 답하지 않는다.""",
                example = "3.0900")
        BigDecimal bandWidthPercent,

        @Schema(description = """
                아래에서 가장 가까운 대. **없을 수 있다** — 지금 가격 아래에 최소 터치 수를
                채운 대가 하나도 없으면 null 이다. 0 으로 채우지 않는다.""",
                nullable = true)
        ZoneResponse support,

        @Schema(description = "위에서 가장 가까운 대. **없을 수 있다**", nullable = true)
        ZoneResponse resistance,

        @Schema(description = """
                최근 스윙의 피보나치 되돌림. **없을 수 있다** — 스윙 고점과 저점 중 한쪽이라도
                안 잡히면 비어 있다. 없는 스윙에 선을 그으면 아무 뜻 없는 여섯 줄이 생긴다.""",
                nullable = true)
        FibonacciResponse fibonacci,

        @Schema(description = """
                매물대 — 거래량이 어느 가격에 몰려 있나. **대와 다른 것을 잰다**: 대는 가격이
                몇 번 되돌아섰나를 세고 매물대는 거기서 얼마나 거래됐나를 센다. 둘이 같은 자리를
                가리키면 그것이 두 개의 증거다.

                **이 수치는 백테스트를 통과한 적이 없다** — 일목·볼린저·대와 같은 무게로 읽으면
                안 된다.""")
        VolumeProfileResponse volume) {

    static TimeframeReadoutResponse from(TimeframeReadout readout) {
        IndicatorReadout indicators = readout.indicators();
        return new TimeframeReadoutResponse(
                readout.interval().code(), readout.at(),
                readout.close().value(), readout.atr().value(),
                indicators.ichimoku().name(), indicators.conversionLine().value(),
                indicators.baseLine().value(), indicators.cloudTop().value(),
                indicators.cloudBottom().value(), indicators.bollinger().name(),
                indicators.bollingerUpper().value(), indicators.bollingerMiddle().value(),
                indicators.bollingerLower().value(), indicators.bandWidthPercent().value(),
                zone(readout.support()), zone(readout.resistance()), fibonacci(readout),
                VolumeProfileResponse.from(readout.volume()));
    }

    /** 없는 대는 {@code null} 이다. 0 으로 채우면 화면에 없는 지지가 생긴다. */
    private static ZoneResponse zone(Optional<ZoneReadout> readout) {
        return readout.map(ZoneResponse::from).orElse(null);
    }

    /** 되돌림은 지금 가격을 함께 받는다 — 골든 포켓 안인가는 둘을 맞대야 나온다. */
    private static FibonacciResponse fibonacci(TimeframeReadout readout) {
        return readout.fibonacci()
                .map(fib -> FibonacciResponse.from(fib, readout.close().value()))
                .orElse(null);
    }
}
