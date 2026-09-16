package com.coinwin.account.adapter.out.binance;

import com.coinwin.account.domain.ProtectiveOrder;
import com.coinwin.account.domain.ProtectiveOrderKind;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * {@code /fapi/v1/openOrders} 응답 한 줄.
 *
 * <p>거래소가 주는 수는 <b>전부 문자열</b>이다. 그대로 두는 이유는 {@code BinancePositionRisk}
 * 와 같다 — 부동소수로 한 번 거치면 0.1 이 0.09999999 가 되고, 수량 비교에 허용 오차를 두지
 * 않기로 한 결정이 거기에 걸려 있다.
 *
 * <p><b>포지션을 여는 주문은 버린다.</b> {@code reduceOnly} 도 {@code closePosition} 도 아니면
 * 진입 지정가이고, 그것을 보호로 세면 "손절이 있다" 는 판정이 그냥 통과한다.
 *
 * <p><b>트리거가 없는 종류는 버린다.</b> {@code reduceOnly} 지정가는 사실상 익절로 쓰이지만
 * 트리거 가격이 없어 어디서 발동하는지 말할 수 없다. 추측해서 {@code price} 를 트리거로
 * 옮기면 롱의 손절 지정가와 익절 지정가가 같은 값이 된다 — <b>모르는 것을 아는 척하지
 * 않는다.</b> 손절 유무 판정은 이것과 무관하다(손절은 언제나 트리거 주문이다).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record BinanceOpenOrder(
        @JsonProperty("symbol") String symbol,
        @JsonProperty("side") String side,
        @JsonProperty("positionSide") String positionSide,
        @JsonProperty("type") String type,
        @JsonProperty("stopPrice") String stopPrice,
        @JsonProperty("origQty") String origQty,
        @JsonProperty("reduceOnly") boolean reduceOnly,
        @JsonProperty("closePosition") boolean closePosition) {

    /**
     * 포지션을 닫는 주문이고, 종류와 덮는 수량을 둘 다 알아볼 수 있는가.
     *
     * <p><b>수량을 모르면 버린다.</b> 전량 주문이 아닌데 {@code origQty} 가 비어 있으면 얼마나
     * 덮는지 말할 수 없고, 그 자리에서 "전량" 으로 읽으면 보호를 과대평가한다 — 이 기능에서
     * 과대평가는 경고가 안 뜨는 것이고 그것이 가장 나쁜 고장이다.
     */
    boolean isProtective() {
        return (reduceOnly || closePosition) && kind().isPresent() && coverageIsKnown();
    }

    private boolean coverageIsKnown() {
        return closePosition || (origQty != null && new BigDecimal(origQty).signum() > 0);
    }

    ProtectiveOrder toDomain() {
        return new ProtectiveOrder(
                new Symbol(symbol), closes(), kind().orElseThrow(), trigger(), quantity());
    }

    /**
     * 어느 방향의 포지션을 닫는가.
     *
     * <p>헤지 모드면 {@code positionSide} 가 그대로 답이다. 단방향 모드에서는 {@code "BOTH"} 가
     * 오므로 매수/매도에서 되짚는다 — <b>매도는 롱을 닫고 매수는 숏을 닫는다.</b>
     */
    private Direction closes() {
        if ("LONG".equals(positionSide) || "SHORT".equals(positionSide)) {
            return Direction.valueOf(positionSide);
        }
        return "SELL".equals(side) ? Direction.LONG : Direction.SHORT;
    }

    private Optional<ProtectiveOrderKind> kind() {
        return switch (type == null ? "" : type) {
            case "STOP_MARKET", "STOP" -> Optional.of(ProtectiveOrderKind.STOP_LOSS);
            case "TAKE_PROFIT_MARKET", "TAKE_PROFIT" ->
                    Optional.of(ProtectiveOrderKind.TAKE_PROFIT);
            case "TRAILING_STOP_MARKET" -> Optional.of(ProtectiveOrderKind.TRAILING_STOP);
            default -> Optional.empty();
        };
    }

    /** 추격 손절은 {@code stopPrice} 가 {@code "0"} 이다. 0 원짜리 트리거로 담지 않는다. */
    private Optional<Price> trigger() {
        if (stopPrice == null || new BigDecimal(stopPrice).signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(Price.of(stopPrice));
    }

    /**
     * 닫을 수량. <b>{@code closePosition} 을 먼저 본다</b> — 전량 주문의 {@code origQty} 는
     * {@code "0"} 이고, 그것을 수량으로 담으면 전량 손절이 "0 만큼 덮는다" 가 된다.
     */
    private Optional<Quantity> quantity() {
        return closePosition ? Optional.empty() : Optional.of(Quantity.of(origQty));
    }
}
