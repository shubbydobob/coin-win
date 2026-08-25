package com.coinwin.readout.api;

import com.coinwin.readout.domain.VolumeProfileReadout;
import com.coinwin.readout.domain.VolumeShelfReadout;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * 이 주기의 매물대가 지금 가격에 대해 말하는 것.
 *
 * <p><b>지지·저항대와 다른 것을 잰다.</b> 대는 가격이 몇 번 되돌아섰나를 세고 매물대는 거기서
 * 얼마나 거래됐나를 센다. 같은 자리를 가리킬 때도 있지만 근거가 다르므로 나란히 놓는다.
 *
 * <p><b>방향을 말하지 않는다.</b> 위의 매물대가 저항이라는 것은 해석이고, 이 저장소는 그런
 * 전제("대에 닿으면 되돌아온다")를 7년 15,110봉으로 반증했다({@code docs/adr/021}).
 *
 * <p><b>이 수치들은 백테스트를 통과한 적이 없다.</b> 일목·볼린저는 트레이딩뷰 원문과 대조했고
 * 대는 15,110봉으로 돌렸지만 매물대는 그런 검증이 없다.
 */
@Schema(description = "거래량이 어느 가격에 몰려 있나")
public record VolumeProfileResponse(

        @Schema(description = """
                가장 두껍게 거래된 가격(POC). **언제나 있다** — 거래가 한 건이라도 있으면
                가장 두꺼운 칸은 정해진다.""",
                example = "79200.00")
        BigDecimal pointOfControl,

        @Schema(description = """
                아래에서 가장 가까운 매물대. **없을 수 있다** — 평균보다 두꺼운 구간이 아래에
                하나도 없으면 null 이다.""", nullable = true)
        VolumeShelfResponse below,

        @Schema(description = "위에서 가장 가까운 매물대. **없을 수 있다**", nullable = true)
        VolumeShelfResponse above,

        @Schema(description = """
                지금 가격을 품고 있는 매물대. **없을 수 있다.** 이것이 비어 있지 않으면
                위·아래가 둘 다 비어 있는 것이 정상이다 — 지금 물린 물량 한가운데에 있다는
                뜻이고, 어느 쪽으로 움직이든 그것을 지나야 한다.""", nullable = true)
        VolumeShelfResponse here) {

    static VolumeProfileResponse from(VolumeProfileReadout readout) {
        return new VolumeProfileResponse(
                readout.pointOfControl().value(),
                shelf(readout.below()),
                shelf(readout.above()),
                shelf(readout.here()));
    }

    /** 없는 매물대는 {@code null} 이다. 0 으로 채우면 화면에 없는 물량이 생긴다. */
    private static VolumeShelfResponse shelf(Optional<VolumeShelfReadout> readout) {
        return readout.map(VolumeShelfResponse::from).orElse(null);
    }
}
