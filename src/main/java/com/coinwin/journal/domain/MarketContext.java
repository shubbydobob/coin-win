package com.coinwin.journal.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.BandPosition;

/**
 * 진입 시점의 시장 상태. <b>왜 들어갔는가</b>를 남기는 자리다.
 *
 * <p>이 프로젝트가 푸는 두 번째 문제가 "매매 이력이 구조화되지 않아 사후 분석이 불가능하다"
 * 이다({@code scope.md}). 손익만 쌓으면 이긴 거래와 진 거래가 구분될 뿐, <b>어떤 상황에서
 * 이겼는지</b> 는 끝내 알 수 없다. 그래서 진입 근거를 필수로 받는다.
 *
 * <p><b>지지·저항은 구조화하지 않고 {@code rationale} 텍스트로 받는다.</b> 저항대를 코드로
 * 정의하는 것은 Phase 6 의 첫 작업이고, 그 정의가 나오기 전에 스키마를 박으면 정의가 확정되는
 * 순간 이미 쌓인 기록을 통째로 못 쓰게 된다. 지금 구조화할 수 있는 것은 지표뿐이다 —
 * 일목과 볼린저는 Phase 4 에서 확정됐다.
 */
public record MarketContext(
        Price priceAtEntry,
        BandPosition ichimokuPosition,
        BandPosition bollingerPosition,
        String rationale) {

    /** DB 컬럼과 맞춘 상한. 넘으면 저장 시점이 아니라 도메인에서 거부한다. */
    static final int MAX_RATIONALE_LENGTH = 500;

    public MarketContext {
        DomainValues.required(priceAtEntry, "진입 시점 가격");
        DomainValues.required(ichimokuPosition, "일목 구름 대비 위치");
        DomainValues.required(bollingerPosition, "볼린저 밴드 대비 위치");
        rationale = normalizeRationale(rationale);
    }

    /** 가격이 일목 구름과 볼린저 밴드 양쪽에서 같은 편에 있는가. 두 필터가 일치한 진입이다. */
    public boolean filtersAgree() {
        return ichimokuPosition == bollingerPosition;
    }

    private static String normalizeRationale(String rationale) {
        DomainValues.required(rationale, "진입 근거");
        String trimmed = rationale.strip();
        if (trimmed.isEmpty()) {
            throw new InvalidTradeException(
                    "진입 근거는 비워 둘 수 없다. 근거 없는 기록은 사후 분석에 쓸 수 없다");
        }
        if (trimmed.length() > MAX_RATIONALE_LENGTH) {
            throw new InvalidValueException(
                    "진입 근거는 %d 자를 넘을 수 없다: %d 자"
                            .formatted(MAX_RATIONALE_LENGTH, trimmed.length()));
        }
        return trimmed;
    }
}
