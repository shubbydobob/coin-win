package com.coinwin.account.domain;

import com.coinwin.common.domain.DomainException;

/**
 * 값은 각각 유효하지만 계좌 상태로는 성립하지 않을 때 던진다.
 *
 * <p>예: 수량이 0 인 포지션. 거래소는 닫힌 종목도 {@code positionAmt: "0"} 으로 돌려주므로
 * 그것을 포지션으로 담으면 "포지션 있음" 이 되어 대조가 통째로 뒤집힌다. 어댑터가 걸러
 * 내되, 걸러 내는 것을 잊었을 때 조용히 통과하지 않도록 도메인에서도 막는다.
 *
 * <p><b>불일치는 예외가 아니다.</b> 기록과 거래소가 어긋나는 것은 오류가 아니라 이 기능이
 * 찾으려는 사실 그 자체다. {@link PositionMatch} 로 표현한다.
 */
public class InvalidAccountDataException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidAccountDataException(String message) {
        super(message);
    }
}
