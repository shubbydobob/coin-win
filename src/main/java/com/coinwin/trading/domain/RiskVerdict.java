package com.coinwin.trading.domain;

import com.coinwin.common.domain.DomainValues;

/**
 * 안전장치가 주문 의도 하나에 내린 판정.
 *
 * <p><b>{@code boolean} 하나로 줄이지 않는다.</b> 거부에는 이유가 있어야 하고, 그 이유가
 * 사이클 기록에 남아야 "봇이 왜 안 들어갔나" 에 답할 수 있다. 참·거짓으로 줄이면 거부가
 * 기록에서 사라지고, <b>아무것도 안 한 것과 막힌 것이 같은 모양이 된다.</b>
 *
 * <p>{@code PositionMatch} 가 네 경우를 타입으로 가른 것과 같은 판단이다.
 */
public sealed interface RiskVerdict {

    /** 이 판정이 가리키는 주문. */
    OrderIntent intent();

    /** 브로커로 보내도 되는가. */
    boolean allowed();

    /** 한계를 넘지 않았다. */
    record Allowed(OrderIntent intent) implements RiskVerdict {

        public Allowed {
            DomainValues.required(intent, "주문 의도");
        }

        @Override
        public boolean allowed() {
            return true;
        }
    }

    /**
     * 한계에 걸렸다.
     *
     * @param reason 사람이 읽을 수 있는 한 문장. 사이클 기록과 화면에 그대로 간다
     */
    record Rejected(OrderIntent intent, String reason) implements RiskVerdict {

        public Rejected {
            DomainValues.required(intent, "주문 의도");
            if (reason == null || reason.isBlank()) {
                throw new InvalidOrderException("거부에는 이유가 있어야 한다");
            }
        }

        @Override
        public boolean allowed() {
            return false;
        }
    }
}
