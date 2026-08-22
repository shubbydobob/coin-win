package com.coinwin.account.application.service;

import com.coinwin.account.application.port.in.ReconcilePositionsUseCase;
import com.coinwin.account.application.port.out.LoadExchangePositionsPort;
import com.coinwin.account.domain.ExchangePosition;
import com.coinwin.account.domain.PositionReconciliation;
import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.journal.application.port.in.QueryJournalUseCase;
import com.coinwin.journal.domain.OpenTrade;
import com.coinwin.market.domain.Symbol;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 기록과 거래소를 읽어 도메인에 넘긴다. 조율만 하고 판정은 하지 않는다.
 *
 * <p><b>{@code journal} 의 인바운드 포트를 소비한다.</b> 방향이 이쪽인 이유는 순환 때문이다 —
 * 기록이 대조를 부르게 두면 {@code journal ↔ account} 가 되고 ArchUnit 규칙 3 이 빌드를
 * 세운다. {@code ai → journal} 과 같은 모양이고, 이유도 같다. <b>기록은 대조를 모른다.</b>
 *
 * <p>아웃바운드가 아니라 인바운드를 쓰는 이유는 "미청산 거래가 무엇인가" 가 {@code journal}
 * 의 정책이기 때문이다. 저장소를 직접 읽으면 그 정책이 여기로 새어 나온다 —
 * {@code position.application → market.application.port.in} 과 같은 판단이다.
 */
@Service
public class PositionReconciliationService implements ReconcilePositionsUseCase {

    private final QueryJournalUseCase journal;
    private final Optional<LoadExchangePositionsPort> exchange;
    private final Symbol symbol;

    /**
     * 포트를 {@code Optional} 로 받는다.
     *
     * <p>필수로 받으면 키가 없는 순간 이 서비스 빈도 못 만들고, 그러면 컨트롤러까지 사라져
     * <b>엔드포인트 자체가 없어진다.</b> 문서에는 있는데 404 가 나는 것보다 "설정되지 않았다"
     * 는 503 이 낫다 — {@code ai} 가 같은 이유로 같은 모양을 쓴다({@code AiPorts}).
     */
    public PositionReconciliationService(
            QueryJournalUseCase journal, Optional<LoadExchangePositionsPort> exchange) {
        this.journal = journal;
        this.exchange = exchange;
        this.symbol = Symbol.BTC_USDT;
    }

    @Override
    public PositionReconciliation reconcile() {
        List<ExchangePosition> positions = connected().positionsFor(symbol);
        return PositionReconciliation.of(openTrades(), positions, observedAt(positions));
    }

    private LoadExchangePositionsPort connected() {
        return exchange.orElseThrow(() -> new ExternalDataUnavailableException(
                "거래소 계정이 연결되지 않았다. "
                        + "COINWIN_ACCOUNT_BINANCE_API_KEY 와 "
                        + "COINWIN_ACCOUNT_BINANCE_SECRET_KEY 환경변수가 필요하다"));
    }

    /**
     * 열려 있는 것만 고른다.
     *
     * <p>{@code activeTrades} 는 <b>세워 둔 계획까지</b> 돌려준다. {@code PlannedTrade} 는
     * 아직 체결되지 않았으므로 거래소에 있을 리가 없고, 그것을 대조에 넣으면 모든 계획이
     * "거래소에 없다" 는 불일치가 된다 — 경고가 언제나 켜져 있으면 아무도 보지 않는다.
     */
    private List<OpenTrade> openTrades() {
        return journal.activeTrades().stream()
                .filter(OpenTrade.class::isInstance)
                .map(OpenTrade.class::cast)
                .toList();
    }

    /**
     * 관측 시각은 <b>거래소가 말한 것</b>을 쓴다. 여기서 {@code Instant.now()} 를 부르면 응답을
     * 기다린 시간만큼 미래가 되고, 화면은 그것을 "이 순간의 사실" 로 읽는다.
     *
     * <p>포지션이 하나도 없으면 거래소가 시각을 말해 주지 않는다. 그때만 지금을 쓴다 —
     * 비어 있다는 사실 자체는 방금 확인한 것이 맞기 때문이다.
     */
    private static Instant observedAt(List<ExchangePosition> positions) {
        return positions.stream()
                .map(ExchangePosition::observedAt)
                .max(Instant::compareTo)
                .orElseGet(Instant::now);
    }
}
