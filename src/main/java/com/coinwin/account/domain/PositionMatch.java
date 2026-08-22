package com.coinwin.account.domain;

import com.coinwin.journal.domain.OpenTrade;
import com.coinwin.position.domain.Direction;

/**
 * 기록 한 건과 거래소 포지션 한 건을 맞춰 본 결과.
 *
 * <p><b>네 경우를 {@code boolean matched} 하나로 줄이지 않는다.</b> 대응이 다른 사실은 타입이
 * 달라야 한다 — Phase 5 가 {@code Trade} 를 상태별로 가른 것과 같은 판단이다. 하나로 줄이면
 * "기록에만 있다" 와 "수량이 다르다" 가 같은 값이 되고, 화면이 그 둘에 다른 말을 할 수 없다.
 *
 * <table>
 *   <caption>각 경우가 뜻하는 것과 사람이 할 일</caption>
 *   <tr><th>경우</th><th>뜻</th><th>할 일</th></tr>
 *   <tr><td>{@link Agreed}</td><td>기록과 거래소가 같다</td><td>없음</td></tr>
 *   <tr><td>{@link RecordedOnly}</td><td>기록은 열려 있는데 거래소에 없다</td>
 *       <td><b>청산을 적었는가</b> — 손절이 체결됐을 수 있다</td></tr>
 *   <tr><td>{@link ExchangeOnly}</td><td>거래소에 있는데 기록에 없다</td>
 *       <td>앱 밖에서 연 포지션이다. 계획을 적을지 정한다</td></tr>
 *   <tr><td>{@link QuantityDiffers}</td><td>둘 다 있는데 수량이 다르다</td>
 *       <td>물타기·부분 청산이 기록되지 않았다</td></tr>
 * </table>
 *
 * <p><b>고쳐 주지 않는다.</b> 기록을 거래소에 맞춰 자동 수정하면 "적기를 빠뜨렸다" 는 사실
 * 자체가 사라진다. 이 타입은 알려 주기만 하고, 고치는 것은 사람이 기록 화면에서 한다.
 */
public sealed interface PositionMatch {

    /** 이 짝이 어느 방향의 포지션에 대한 것인가. 표에서 줄을 가리키는 데 쓴다. */
    Direction direction();

    /** 사람이 확인할 것이 있는가. */
    boolean isDiscrepancy();

    /** 기록과 거래소가 방향·수량 모두 같다. */
    record Agreed(OpenTrade recorded, ExchangePosition actual) implements PositionMatch {

        public Agreed {
            requireBoth(recorded, actual);
        }

        @Override
        public Direction direction() {
            return recorded.plan().direction();
        }

        @Override
        public boolean isDiscrepancy() {
            return false;
        }
    }

    /**
     * 기록에는 열려 있는데 거래소에 없다.
     *
     * <p><b>가장 흔하고 가장 조용한 오류다.</b> 손절이 체결됐는데 청산을 적지 않으면 이 상태가
     * 되고, 그동안 집계는 그 거래를 없는 것으로 센다 — 실현 손익도 계획 준수율도 틀린다.
     */
    record RecordedOnly(OpenTrade recorded) implements PositionMatch {

        public RecordedOnly {
            if (recorded == null) {
                throw new InvalidAccountDataException("기록된 거래가 없다");
            }
        }

        @Override
        public Direction direction() {
            return recorded.plan().direction();
        }

        @Override
        public boolean isDiscrepancy() {
            return true;
        }
    }

    /** 거래소에 포지션이 있는데 기록에 없다. 앱 밖에서 열었다는 뜻이다. */
    record ExchangeOnly(ExchangePosition actual) implements PositionMatch {

        public ExchangeOnly {
            if (actual == null) {
                throw new InvalidAccountDataException("거래소 포지션이 없다");
            }
        }

        @Override
        public Direction direction() {
            return actual.direction();
        }

        @Override
        public boolean isDiscrepancy() {
            return true;
        }
    }

    /**
     * 둘 다 있는데 수량이 다르다.
     *
     * <p><b>허용 오차를 두지 않는다.</b> {@code Quantity} 는 스케일 8 이고 그 자리까지 정확히
     * 같아야 일치다. 오차를 두면 그 값이 어디서 왔는지 설명할 수 없고, 부분 청산이 조용히
     * 통과한다.
     */
    record QuantityDiffers(OpenTrade recorded, ExchangePosition actual) implements PositionMatch {

        public QuantityDiffers {
            requireBoth(recorded, actual);
        }

        @Override
        public Direction direction() {
            return recorded.plan().direction();
        }

        @Override
        public boolean isDiscrepancy() {
            return true;
        }
    }

    private static void requireBoth(OpenTrade recorded, ExchangePosition actual) {
        if (recorded == null || actual == null) {
            throw new InvalidAccountDataException("짝지어진 결과는 양쪽이 모두 있어야 한다");
        }
        if (recorded.plan().direction() != actual.direction()) {
            throw new InvalidAccountDataException(
                    "방향이 다른 것은 같은 포지션이 아니다: 기록 %s, 거래소 %s"
                            .formatted(recorded.plan().direction(), actual.direction()));
        }
    }
}
