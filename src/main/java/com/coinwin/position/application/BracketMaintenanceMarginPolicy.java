package com.coinwin.position.application;

import com.coinwin.common.domain.Money;
import com.coinwin.market.application.port.in.LoadLeverageBracketsUseCase;
import com.coinwin.market.domain.LeverageBracket;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.MaintenanceMargin;
import com.coinwin.position.domain.MaintenanceMarginPolicy;

/**
 * 거래소 레버리지 구간표에서 유지증거금 규칙을 고른다. ADR 008 이 예고한 두 번째 구현체다.
 *
 * <p><b>왜 {@code position/application} 인가.</b> {@code position/domain} 에 두면
 * {@code position → market} 방향 의존이 도메인에 생긴다. 어댑터에 두자니 이것은 HTTP 도
 * DB 도 아니고 <b>두 모듈을 잇는 정책</b>이다. 도메인 인터페이스를 구현하면서 다른 모듈의
 * 인바운드 포트를 소비하는 자리는 application 층뿐이다.
 *
 * <p>{@code market} 의 아웃바운드 포트가 아니라 <b>인바운드</b> 포트를 쓴다. 아웃바운드를
 * 직접 쓰면 "어디서 구간표를 얻는가" 라는 {@code market} 의 정책이 {@code position} 으로
 * 새어 나온다.
 *
 * <p>종목을 생성자에서 고정하는 이유는 {@link MaintenanceMarginPolicy} 가 명목가만 받기
 * 때문이다. 1인·단일 종목 전제이므로 이것으로 충분하다. 종목이 늘면 그때 시그니처가 는다.
 */
public class BracketMaintenanceMarginPolicy implements MaintenanceMarginPolicy {

    private final LoadLeverageBracketsUseCase brackets;
    private final Symbol symbol;

    public BracketMaintenanceMarginPolicy(LoadLeverageBracketsUseCase brackets, Symbol symbol) {
        this.brackets = brackets;
        this.symbol = symbol;
    }

    /**
     * {@inheritDoc}
     *
     * <p>명목가가 마지막 구간을 넘으면 {@code market} 이 예외를 던진다. 조용히 마지막 구간을
     * 쓰지 않는 이유는, 그런 포지션이 거래소에서 애초에 열리지 않기 때문이다.
     */
    @Override
    public MaintenanceMargin requirementFor(Money notional) {
        LeverageBracket bracket = brackets.bracketsFor(symbol).forNotional(notional);
        return new MaintenanceMargin(
                bracket.maintenanceMarginRate(), bracket.maintenanceAmount());
    }
}
