package com.coinwin.trading.adapter.out.market;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.market.application.port.in.LoadMarketDataUseCase;
import com.coinwin.market.application.port.in.LoadOrderBookUseCase;
import com.coinwin.market.application.port.in.LoadOutliersUseCase;
import com.coinwin.market.application.port.in.SyncMarketDataUseCase;

/**
 * 봇이 시장을 보기 위해 쓰는 인바운드 포트 넷.
 *
 * <p>묶은 이유는 파라미터 한계(4) 때문만이 아니다. <b>넷은 전부 "시장을 어떻게 얻는가" 라는
 * 한 가지 사정</b>이고, 따로 다니면 조립하는 쪽이 시장 넷과 장부 둘을 같은 줄에 늘어놓게 된다.
 *
 * @param tickers 현재가와 호가
 * @param candles 저장된 봉을 읽는다. <b>거래소를 때리지 않는다</b> — 그 덕분에 백테스트가
 *     결정론을 갖는다
 * @param sync 거래소에서 봉을 채운다. <b>읽기와 나뉘어 있다</b>
 * @param outliers 펀딩비·미결제약정·롱숏비율·테이커비율·상위 계정 포지션의 분위와 변화
 */
public record MarketAccess(
        LoadOrderBookUseCase tickers,
        LoadMarketDataUseCase candles,
        SyncMarketDataUseCase sync,
        LoadOutliersUseCase outliers) {

    public MarketAccess {
        DomainValues.required(tickers, "시세");
        DomainValues.required(candles, "캔들");
        DomainValues.required(sync, "캔들 동기화");
        DomainValues.required(outliers, "이상치");
    }
}
