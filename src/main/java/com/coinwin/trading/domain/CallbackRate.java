package com.coinwin.trading.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 추격 손절이 따라오는 폭. <b>가격이 유리한 쪽으로 간 최고점에서 이만큼 되돌아오면 닫는다.</b>
 *
 * <p>퍼센트를 그대로 쓰지 않고 타입으로 감싼 이유는 <b>감시 화면이 호가 단수에서 배운 것</b>
 * 때문이다. 그때는 "1 과 20 사이" 라는 범위가 서비스에 적혀 있었고 그것은 <b>우리가 상상한
 * 것</b>이지 거래소가 정한 것이 아니었다 — 바이낸스가 {@code -4021} 로 거절했고, 계약
 * 테스트는 인메모리 구현이 그 값을 받아 주는 바람에 지나갔다. 값을 타입으로 막으면 장부
 * 브로커도 거래소가 거절할 값을 만들 수 없다.
 *
 * <p><b>그리고 이 셋은 확인하지 못했다</b> — 최소 0.1 · 최대 10 · 소수 한 자리. 이 환경의
 * 네트워크 정책이 바이낸스를 막아 문서도 엔드포인트도 읽을 수 없었다
 * ({@code exit-automation.md} § 8 이 남겨 둔 자리).
 *
 * <p>그래서 <b>틀렸을 때 어떻게 드러나는가</b>를 대신 설계했다. 우리가 거래소보다 좁으면
 * 앱이 뜨면서 던지고, 넓으면 거래소가 주문을 거절한다 — <b>둘 다 시끄럽다.</b> 조용히
 * 틀린 값이 나가서 돈이 잘못 움직이는 경로는 없다. 확인은 테스트넷에 사람이 한 번 붙여
 * 보는 것이고, 그때까지 이 수는 자리표시자다.
 */
public record CallbackRate(Percentage value) {

    /** 거래소가 정한 것으로 <b>읽은</b> 하한. 확인하지 못했다. */
    private static final BigDecimal MIN = new BigDecimal("0.1");

    /** 거래소가 정한 것으로 <b>읽은</b> 상한. 확인하지 못했다. */
    private static final BigDecimal MAX = new BigDecimal("10");

    public CallbackRate {
        DomainValues.required(value, "추격 폭");
        assertInRange(value.value());
        assertOneDecimal(value.value());
    }

    public static CallbackRate of(String percent) {
        return new CallbackRate(Percentage.of(percent));
    }

    public static CallbackRate of(BigDecimal percent) {
        return new CallbackRate(Percentage.of(percent));
    }

    /**
     * 거래소에 보낼 값. <b>소수 한 자리로 적는다</b> — {@link Percentage} 는 스케일 4 이므로
     * 그대로 보내면 {@code 1.0000} 이 나가고, 거래소가 그것을 받아 주는지는 확인하지 못했다.
     * 받는 것이 확실한 모양(자기가 응답에 쓰는 모양)으로 맞춘다.
     */
    public BigDecimal asPercent() {
        return value.value().setScale(1, RoundingMode.UNNECESSARY);
    }

    /**
     * 최고점(숏이면 최저점)이 여기였을 때 손절이 터지는 가격.
     *
     * <p><b>이 계산이 도메인에 있는 이유</b>는 장부 브로커가 그것으로 체결을 흉내 내기
     * 때문이다. 어댑터 안에 두면 모의 기록과 실계좌 기록이 다른 자로 재게 되고, 그러면 둘을
     * 나란히 놓는 것이 이 봇의 유일한 검증 방법인데 그 대조가 무의미해진다
     * ({@code trading-bot.md} § 5).
     */
    public Price stopFrom(Price extreme, Direction position) {
        DomainValues.required(extreme, "극값");
        DomainValues.required(position, "포지션 방향");
        BigDecimal fraction = value.asFraction();
        return extreme.multipliedBy(position == Direction.LONG
                ? BigDecimal.ONE.subtract(fraction)
                : BigDecimal.ONE.add(fraction));
    }

    private static void assertInRange(BigDecimal percent) {
        if (percent.compareTo(MIN) < 0 || percent.compareTo(MAX) > 0) {
            throw new InvalidOrderException(
                    "추격 폭은 %s%% 와 %s%% 사이여야 한다: %s".formatted(
                            MIN.toPlainString(), MAX.toPlainString(), percent.toPlainString()));
        }
    }

    /** 소수 둘째 자리 이하는 버리지 않고 거부한다. 조용히 반올림하면 건 것과 도는 것이 달라진다. */
    private static void assertOneDecimal(BigDecimal percent) {
        if (percent.stripTrailingZeros().scale() > 1) {
            throw new InvalidOrderException(
                    "추격 폭은 소수 한 자리까지다: " + percent.toPlainString());
        }
    }

    /** 사람이 읽는 한 조각. {@code 1.0%} 처럼 나온다. */
    public String describe() {
        return asPercent().toPlainString() + "%";
    }
}
