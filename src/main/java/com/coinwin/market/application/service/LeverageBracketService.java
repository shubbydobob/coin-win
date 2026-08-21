package com.coinwin.market.application.service;

import com.coinwin.market.application.port.in.LoadLeverageBracketsUseCase;
import com.coinwin.market.application.port.out.LoadLeverageBracketsPort;
import com.coinwin.market.domain.LeverageBrackets;
import com.coinwin.market.domain.Symbol;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 레버리지 구간표를 한 번 읽고 들고 있는다.
 *
 * <p>캐싱하는 이유는 이 값이 <b>청산가를 구할 때마다</b> 필요하기 때문이다. 계획 하나를
 * 분석하면 체결 상태 수만큼 조회가 일어나고, 그때마다 파일을 다시 파싱하고 구간표 정합성을
 * 다시 검사하는 것은 낭비다.
 *
 * <p>무효화 경로가 없다. 스냅샷 파일은 애플리케이션이 도는 동안 바뀌지 않는다 — 갱신은 파일을
 * 교체하고 다시 띄우는 일이다. 무효화가 필요해지는 날은 거래소에서 직접 받아 오게 되는
 * 날이고, 그때 이 자리에 TTL 이 붙는다.
 */
@Service
public class LeverageBracketService implements LoadLeverageBracketsUseCase {

    private final LoadLeverageBracketsPort bracketsPort;
    private final Map<Symbol, LeverageBrackets> cached = new ConcurrentHashMap<>();

    public LeverageBracketService(LoadLeverageBracketsPort bracketsPort) {
        this.bracketsPort = bracketsPort;
    }

    @Override
    public LeverageBrackets bracketsFor(Symbol symbol) {
        return cached.computeIfAbsent(symbol, bracketsPort::bracketsFor);
    }
}
