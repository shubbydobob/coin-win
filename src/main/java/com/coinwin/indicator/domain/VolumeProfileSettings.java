package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import java.math.BigDecimal;

/**
 * 매물대를 만드는 설정.
 *
 * <p><b>둘 다 상대값이라 종목과 주기에 종속되지 않는다.</b> 구간 수는 관측된 가격 범위를
 * 나누는 것이고 배수는 평균 대비이므로, 4시간봉이든 15분봉이든 같은 설정을 쓴다.
 * {@code ZoneSettings} 가 봉 수와 ATR 배수만 갖는 것과 같은 이유다.
 *
 * @param bins 관측된 가격 범위를 몇 칸으로 나눌 것인가. 늘리면 매물대가 잘게 쪼개지고 줄이면
 *     서로 다른 자리가 한 칸에 뭉친다
 * @param heavyMultiple 평균의 몇 배부터 매물대로 볼 것인가. 1 이면 절반 가까이가 매물대가 되어
 *     아무것도 가리키지 않는다
 */
public record VolumeProfileSettings(int bins, BigDecimal heavyMultiple) {

    public VolumeProfileSettings {
        DomainValues.atLeast(bins, 2, "구간 수");
        DomainValues.required(heavyMultiple, "매물대 배수");
        if (heavyMultiple.compareTo(BigDecimal.ONE) <= 0) {
            throw new InvalidValueException(
                    "매물대 배수는 1 보다 커야 한다: " + heavyMultiple.toPlainString());
        }
    }

    /**
     * 출발점이지 결론이 아니다.
     *
     * <p>24칸은 300봉짜리 판독에서 한 칸이 눈에 띄는 폭이 되도록 고른 값이고, 1.5배는 평균의
     * 절반만큼 더 두꺼운 칸만 남긴다는 뜻이다. <b>이 두 수는 백테스트로 검증되지 않았다</b> —
     * 지지·저항대와 달리 매물대는 아직 어떤 전략도 이것으로 거래를 내 본 적이 없다.
     */
    public static VolumeProfileSettings standard() {
        return new VolumeProfileSettings(24, new BigDecimal("1.5"));
    }
}
