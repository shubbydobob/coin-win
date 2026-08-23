package com.coinwin.market.domain;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 지금 값이 <b>어느 쪽 진영인가</b>. 붐비는 쪽이라는 뜻이지 유리한 쪽이라는 뜻이 아니다.
 *
 * <p>화면이 "상위 96.7%" 까지만 말하고 그것이 롱 쪽인지 숏 쪽인지를 말하지 않는 것이 이 타입을
 * 만든 이유다. 지표 다섯 중 넷은 <b>중립점을 기준으로 부호가 곧 진영</b>인데 그 사실이 어디에도
 * 그려져 있지 않았다.
 *
 * <p><b>여기까지가 사실이고 그 다음은 아니다.</b> 펀딩비가 양수면 롱이 숏에게 비용을 낸다는 것은
 * 정의이지 해석이 아니다. 그런데 "롱이 붐비니 숏이 유리하다" 는 정의가 아니라 예측이고, 이
 * 저장소는 그 예측에 증거가 없다 — {@code docs/adr/021} 이 같은 종류의 전제("대에 닿으면
 * 되돌아온다")를 7년 15,110봉에서 반증했다. 그래서 이 타입은 진영까지만 말하고 우열은 말하지
 * 않는다({@code scope.md}).
 *
 * <p>{@link #NONE} 과 {@link #BALANCED} 는 다르다. 미결제약정은 <b>축 자체가 없고</b>(크기이지
 * 방향이 아니다), 롱숏비율 1.0 은 축 위의 정확히 가운데다. 둘을 섞으면 "방향 있는 지표 몇 개
 * 중 몇 개가 롱 쪽인가" 를 셀 수 없다.
 */
public enum CrowdedSide {

    /** 롱 쪽이 붐빈다. */
    LONG,

    /** 숏 쪽이 붐빈다. */
    SHORT,

    /** 축 위의 정확히 중립점. 양쪽이 같다. */
    BALANCED,

    /** 축이 없는 지표. 미결제약정과 가격이 그렇다. */
    NONE;

    /** 중립점과 견주어 진영을 정한다. 중립점이 없는 지표는 {@link #NONE} 이다. */
    public static CrowdedSide of(MetricKind kind, BigDecimal current) {
        Optional<BigDecimal> neutral = kind.neutral();
        if (neutral.isEmpty() || current == null) {
            return NONE;
        }
        return switch (Integer.signum(current.compareTo(neutral.get()))) {
            case 1 -> LONG;
            case -1 -> SHORT;
            default -> BALANCED;
        };
    }
}
