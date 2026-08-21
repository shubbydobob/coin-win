package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import java.math.BigDecimal;

/**
 * 신호를 계획으로 바꿀 때 쓰는 규칙. 전부 무차원 수이므로 타임프레임에 종속되지 않는다.
 *
 * @param stopBufferMultiple 손절을 대 원단에서 얼마나 더 밀 것인가. 단위는 ATR 배수
 * @param minRiskReward 이 값 미만이면 신호를 버린다. Phase 1 은 경고로 남겨 두지만
 *     백테스트에서는 사람이 매번 판단할 수 없으므로 규칙으로 올린다
 * @param indicatorFilter 일목·볼린저를 진입 게이트로 쓸 것인가. 끄면 판정을 기록만 한다
 */
public record EntryRules(
        BigDecimal stopBufferMultiple, BigDecimal minRiskReward, boolean indicatorFilter) {

    public EntryRules {
        DomainValues.required(stopBufferMultiple, "손절 버퍼 배수");
        DomainValues.required(minRiskReward, "최소 손익비");
        if (stopBufferMultiple.signum() < 0) {
            throw new InvalidBacktestException(
                    "손절 버퍼 배수는 음수일 수 없다: " + stopBufferMultiple.toPlainString());
        }
        if (minRiskReward.signum() <= 0) {
            throw new InvalidBacktestException(
                    "최소 손익비는 0 보다 커야 한다: " + minRiskReward.toPlainString());
        }
    }
}
