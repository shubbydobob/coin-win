package com.coinwin.readout.application;

import com.coinwin.backtest.domain.ZoneSettings;
import com.coinwin.indicator.domain.VolumeProfileSettings;
import com.coinwin.market.application.port.in.LoadMarketDataUseCase;
import com.coinwin.market.application.port.in.SyncMarketDataUseCase;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.TimeRange;
import com.coinwin.readout.domain.TimeframeReadout;
import com.coinwin.readout.domain.TimeframeSeries;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 여러 주기를 한 번에 판독한다.
 *
 * <p><b>왜 한 응답인가.</b> 15분·1시간·4시간을 따로 부르면 세 응답이 서로 다른 순간의 사실이
 * 되고, 화면은 그것을 나란히 놓는다. <b>주기가 다른 것과 시점이 다른 것은 전혀 다른 문제다</b> —
 * 감시 화면이 폴링을 3초로 맞춘 것과 같은 판단이다.
 *
 * <p><b>캔들은 인바운드 포트로 얻는다.</b> "저장된 것으로 충분한가, 거래소에서 채울 것인가" 는
 * {@code market} 의 정책이고, 저장소를 직접 읽으면 그 정책이 이쪽으로 샌다.
 * {@code position.application → market.application.port.in} 과 같은 모양이다.
 *
 * <p><b>매물대 설정은 여기 기본값을 쓴다.</b> 대와 달리 백테스트에 대응하는 설정이 없다 —
 * 어떤 전략도 아직 매물대로 거래를 내 본 적이 없기 때문이다. 그 사실이 이 화면에서 매물대를
 * 검증된 지표와 같은 무게로 읽으면 안 되는 이유다.
 *
 * <p><b>대 설정은 백테스트가 쓰는 그것이다.</b> 화면이 보여 주는 지지·저항은 7년으로 검증한
 * 규칙에서 나와야 한다. 여기 별도 기본값을 두면 그 순간 둘이 갈라지고, 그러면 검증이 화면에
 * 대해 아무것도 말해 주지 않는다.
 *
 * <p><b>인터페이스를 두지 않는다.</b> 계층형 모듈이고 구현이 하나뿐이다 —
 * {@code architecture.md} 의 "구현체가 하나뿐인 인터페이스는 만들지 않는다" 가 그대로 걸린다.
 * 포트가 필요해지는 것은 어댑터가 둘이 될 때이지 계층을 나눌 때가 아니다.
 */
@Service
public class ReadoutService {

    /**
     * 어느 주기를 함께 놓는가.
     *
     * <p>짧은 것부터다 — 진입 자리는 15분에서 보고, 그 판단이 상위 주기와 어긋나는지를
     * 1시간·4시간에서 확인한다. 순서를 여기서 정해 두면 화면이 다시 정렬하지 않는다.
     */
    private static final List<CandleInterval> INTERVALS = List.of(
            CandleInterval.FIFTEEN_MINUTES,
            CandleInterval.ONE_HOUR,
            CandleInterval.FOUR_HOURS,
            CandleInterval.ONE_DAY,
            CandleInterval.ONE_WEEK);

    /**
     * 몇 봉을 보는가.
     *
     * <p>일목 워밍업이 52봉이고 선행스팬까지 유효해지려면 변위 25봉이 더 필요하다. 대는 피벗을
     * 세는 것이라 봉이 많을수록 안정된다. 300봉이면 4시간봉으로 50일이고, 그보다 늘리면 지금
     * 가격에서 한참 떨어진 옛 대만 늘어난다.
     */
    private static final int BARS = 300;

    private final LoadMarketDataUseCase marketData;

    private final SyncMarketDataUseCase syncMarketData;

    private final Clock clock;

    public ReadoutService(
            LoadMarketDataUseCase marketData, SyncMarketDataUseCase syncMarketData, Clock clock) {
        this.marketData = marketData;
        this.syncMarketData = syncMarketData;
        this.clock = clock;
    }

    /**
     * 한 주기의 캔들과 지표 곡선. <b>판독과 같은 캔들을 본다.</b>
     *
     * <p>{@link #readAll} 과 채우는 방식이 같으므로 같은 봉 위에서 나온다 — 요약 줄의 값과
     * 차트가 다른 시점을 말하면 안 된다.
     */
    public TimeframeSeries series(Symbol symbol, CandleInterval interval) {
        return TimeframeSeries.over(interval, fill(symbol, interval, clock.instant()));
    }

    /** 세 주기를 <b>같은 시각 기준으로</b> 판독한다. */
    public List<TimeframeReadout> readAll(Symbol symbol) {
        Instant now = clock.instant();
        return INTERVALS.stream().map(interval -> read(symbol, interval, now)).toList();
    }

    /**
     * 한 주기를 채우고 읽는다.
     *
     * <p><b>채우는 것과 읽는 것이 나뉘어 있다.</b> {@code LoadMarketDataUseCase} 는 저장된 것만
     * 읽고 거래소를 때리지 않는다 — 그 덕분에 백테스트가 결정론을 갖는다. 판독기는 반대로
     * <b>지금 값</b>이 필요하므로 먼저 채운 뒤 읽는다.
     *
     * <p>처음에는 읽기만 했고, 15분봉이 저장된 적이 없어 <b>빈 목록을 받아 500 으로 죽었다.</b>
     * 포트 문서가 "모자라면 채워서 준다" 고 적고 있었는데 구현은 그렇지 않았고, 그 문서를
     * 믿은 첫 소비자가 이 판독기였다.
     *
     * <p>저장은 같은 봉을 다시 넣어도 안전하다 — 저장소가 기본키로 걸러낸다. 그래서 매번
     * 같은 구간을 채워도 새로 들어가는 것은 그 사이 생긴 봉뿐이다.
     */
    private TimeframeReadout read(Symbol symbol, CandleInterval interval, Instant now) {
        return TimeframeReadout.over(
                interval,
                fill(symbol, interval, now),
                ZoneSettings.standard(),
                VolumeProfileSettings.standard());
    }

    /** 거래소에서 채운 뒤 저장된 것을 읽는다. 판독과 곡선이 이 한 곳을 함께 쓴다. */
    private CandleSeries fill(Symbol symbol, CandleInterval interval, Instant now) {
        TimeRange range = new TimeRange(now.minus(interval.length().multipliedBy(BARS)), now);
        CandleQuery query = new CandleQuery(symbol, interval, range);
        syncMarketData.sync(query);
        return marketData.candles(query);
    }
}
