package com.coinwin.market.domain;

/**
 * 이상치를 재는 지표와 <b>그 지표의 표본 정책</b>.
 *
 * <p>표본 수를 여기 두는 이유는 지표마다 주기가 다르기 때문이다 — 펀딩비는 8시간마다 갱신되어
 * 90개가 30일이고, 미결제약정은 5분마다라 30개가 2시간 반이다. 그 수를 서비스에 두면 "왜 90인가"
 * 가 코드에서 사라진다.
 *
 * <p>{@code minimumSamples} 는 <b>이만큼은 있어야 위치를 말한다</b> 는 선이다. 그 아래면
 * {@code MetricHistory} 가 비어 있는 결과를 낸다.
 *
 * <p><b>변화의 창도 지표가 갖는다.</b> 모든 지표에 "최근 30분" 을 쓰면 펀딩비에서 무의미해진다 —
 * 8시간마다 갱신되므로 30분 안에는 같은 값이 그대로 있다. 근거:
 * {@code docs/spec/market-watch.md} § 6.5.2
 */
public enum MetricKind {

    /** 8시간 주기. 90개면 30일이고, 변화는 최근 3회(하루)로 본다. */
    FUNDING_RATE(90, 30, 3, Change.DIFFERENCE),

    /** 5분 주기. 30개면 2시간 반이고, 변화는 최근 6개(30분)로 본다. */
    OPEN_INTEREST(30, 20, 6, Change.RATIO),

    LONG_SHORT_RATIO(30, 20, 6, Change.RATIO),

    /** 시장가로 때린 쪽의 비율. 호가와 달리 <b>이미 체결된 것</b>이라 취소가 없다. */
    TAKER_RATIO(30, 20, 6, Change.RATIO),

    /** 상위 계정의 롱숏비. <b>포지션 크기 기준</b>이라 "큰손이 어느 쪽에 얼마나" 를 말한다. */
    TOP_POSITION_RATIO(30, 20, 6, Change.RATIO),

    /**
     * 가격. 지표가 아니라 <b>나란히 놓기 위한 기준선</b>이다.
     *
     * <p>미결제약정 −3.2% 는 가격 +1.1% 옆에서만 뜻이 된다 — 포지션이 줄면서 가격이 올랐다면
     * 청산이고, 포지션이 줄면서 가격도 내렸다면 그냥 손을 턴 것이다.
     */
    PRICE(30, 20, 6, Change.RATIO);

    /**
     * 변화를 비율로 말할 것인가 차이로 말할 것인가.
     *
     * <p><b>펀딩비는 부호가 바뀐다.</b> +0.001% 에서 −0.05% 로 갔을 때 비율은 −5000% 이고 그
     * 수는 아무 뜻이 없다. 0 근처에서 무한대로 튀는 것도 배수와 같은 문제다. 그래서 펀딩비만
     * 차이(%p)로 말한다.
     */
    public enum Change {
        RATIO,
        DIFFERENCE
    }

    private final int sampleSize;
    private final int minimumSamples;
    private final int recentWindow;
    private final Change change;

    MetricKind(int sampleSize, int minimumSamples, int recentWindow, Change change) {
        this.sampleSize = sampleSize;
        this.minimumSamples = minimumSamples;
        this.recentWindow = recentWindow;
        this.change = change;
    }

    public int sampleSize() {
        return sampleSize;
    }

    public int minimumSamples() {
        return minimumSamples;
    }

    /** 변화를 재는 창. 표본 <b>마지막</b>에서 이만큼을 본다. */
    public int recentWindow() {
        return recentWindow;
    }

    public Change change() {
        return change;
    }

    /**
     * 이 지표의 <b>중립점</b>. 값이 이보다 크면 롱 쪽, 작으면 숏 쪽이다.
     *
     * <p>비어 있다는 것은 <b>축이 없다</b>는 뜻이지 중립이라는 뜻이 아니다. 미결제약정은 크기이지
     * 방향이 아니고, 가격은 기준선이다. 둘 다 "몇이면 롱 쪽인가" 라는 질문이 성립하지 않는다.
     *
     * <p>열거 상수의 필드가 아니라 {@code switch} 인 이유는 생성자 인자가 이미 넷이기 때문이다
     * (컨벤션 한계). 그리고 이 모양이 더 낫다 — 지표를 새로 넣으면 <b>여기서 컴파일이 멈추고</b>
     * "이것은 어느 쪽에 실리는 값인가" 를 반드시 한 번 답하게 된다.
     */
    public java.util.Optional<java.math.BigDecimal> neutral() {
        return switch (this) {
            // 0 을 넘으면 롱이 숏에게 낸다.
            case FUNDING_RATE -> java.util.Optional.of(java.math.BigDecimal.ZERO);
            // 셋 다 롱 ÷ 숏 이므로 1 이 양쪽이 같은 지점이다.
            case LONG_SHORT_RATIO, TAKER_RATIO, TOP_POSITION_RATIO ->
                    java.util.Optional.of(java.math.BigDecimal.ONE);
            case OPEN_INTEREST, PRICE -> java.util.Optional.empty();
        };
    }

    /** 화면에 놓이는 지표들. {@link #PRICE} 는 기준선이라 빠진다. */
    public static java.util.List<MetricKind> displayed() {
        return java.util.List.of(
                FUNDING_RATE, OPEN_INTEREST, LONG_SHORT_RATIO, TAKER_RATIO, TOP_POSITION_RATIO);
    }
}
