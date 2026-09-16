package com.coinwin.account.application.service;

import com.coinwin.account.application.port.in.ReconcilePositionsUseCase;
import com.coinwin.account.application.port.in.ReviewStopLossUseCase;
import com.coinwin.account.application.port.out.LoadOpenOrdersPort;
import com.coinwin.account.domain.PositionProtectionReview;
import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.market.domain.Symbol;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 대조 결과 위에 미체결 주문을 얹어 "손절이 걸려 있나" 를 낸다. 조율만 하고 판정은 도메인이 한다.
 *
 * <p><b>대조를 다시 하지 않는다.</b> {@link ReconcilePositionsUseCase} 를 그대로 부른다 —
 * 방향으로 짝짓는 규칙과 "미청산 거래가 무엇인가" 가 이미 그쪽에 있고, 여기서 또 짜면 두
 * 화면이 다른 답을 낼 수 있다.
 *
 * <p>같은 모듈의 인바운드 포트를 소비하는 것이 처음이다. 서비스를 직접 들지 않는 이유는
 * 바깥 모듈에 대고 세운 규칙과 같다 — 무엇이 미청산인가는 그쪽의 정책이고, 이 서비스는
 * 그 정책의 결과만 받는다.
 */
@Service
public class StopLossReviewService implements ReviewStopLossUseCase {

    private final ReconcilePositionsUseCase reconcile;
    private final Optional<LoadOpenOrdersPort> orders;
    private final Symbol symbol;

    /**
     * 포트를 {@code Optional} 로 받는다. 키가 없으면 이 자리만 비활성이고 앱은 그대로 뜬다 —
     * {@code PositionReconciliationService} 와 같은 이유, 같은 모양이다.
     */
    public StopLossReviewService(
            ReconcilePositionsUseCase reconcile, Optional<LoadOpenOrdersPort> orders) {
        this.reconcile = reconcile;
        this.orders = orders;
        this.symbol = Symbol.BTC_USDT;
    }

    @Override
    public PositionProtectionReview review() {
        return PositionProtectionReview.of(
                reconcile.reconcile(), connected().protectiveOrdersFor(symbol));
    }

    private LoadOpenOrdersPort connected() {
        return orders.orElseThrow(() -> new ExternalDataUnavailableException(
                "거래소 계정이 연결되지 않았다. "
                        + "COINWIN_ACCOUNT_BINANCE_API_KEY 와 "
                        + "COINWIN_ACCOUNT_BINANCE_SECRET_KEY 환경변수가 필요하다"));
    }
}
