package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Quantity;

/**
 * 매물대 하나. 평균보다 두꺼운 칸들이 붙어 있으면 한 덩이로 본다.
 *
 * <p><b>매물대에는 폭이 있다.</b> 한 칸만 가리키면 "78,200 에 매물대" 처럼 읽히지만 실제로
 * 사람들이 물려 있는 것은 구간이고, 뚫렸는지는 반대편 끝까지 가 봐야 안다 — 지지·저항대가
 * 모서리 둘을 갖는 것과 같은 이유다.
 *
 * <p><b>방향을 말하지 않는다.</b> 매물대가 위에 있다고 못 오른다는 뜻이 아니고 아래에 있다고
 * 받쳐 준다는 뜻도 아니다. 담는 것은 "여기서 이만큼 오갔다" 까지이며, {@link PriceBand} 를
 * 공유하는 다른 지표들과 같은 태도다.
 *
 * @param band 매물대의 두 모서리
 * @param volume 이 구간에서 오간 거래량
 * @param share 전체 거래량의 몇 %인가. <b>두께를 종목·주기와 무관하게 견주게 하는 수다</b> —
 *     BTC 수량은 기간이 길수록 커져서 그 자체로는 두꺼운지 알 수 없다
 */
public record VolumeShelf(PriceBand band, Quantity volume, Percentage share) {

    public VolumeShelf {
        DomainValues.required(band, "매물대 구간");
        DomainValues.required(volume, "거래량");
        DomainValues.required(share, "거래량 비중");
    }
}
