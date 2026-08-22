package com.coinwin.account.application.port.in;

import com.coinwin.account.domain.PositionReconciliation;

/** 기록과 거래소를 맞춰 본다. */
public interface ReconcilePositionsUseCase {

    /**
     * 지금 이 순간의 대조.
     *
     * @throws com.coinwin.common.domain.ExternalDataUnavailableException 거래소를 읽지 못했을 때
     */
    PositionReconciliation reconcile();
}
