package com.coinwin.account.application.port.in;

import com.coinwin.account.domain.PositionProtectionReview;

/** 열려 있는 포지션에 손절이 걸려 있는지 물어본다. */
public interface ReviewStopLossUseCase {

    /**
     * 지금 이 순간의 보호 판정.
     *
     * @throws com.coinwin.common.domain.ExternalDataUnavailableException 거래소를 읽지 못했을 때
     */
    PositionProtectionReview review();
}
