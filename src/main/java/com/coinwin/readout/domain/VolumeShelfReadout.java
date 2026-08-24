package com.coinwin.readout.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.VolumeShelf;

/**
 * 지금 가격 기준으로 읽은 매물대 하나.
 *
 * <p><b>대와 나란히 놓으려고 같은 모양을 갖는다.</b> 다른 것은 근거뿐이다 — 대는 몇 번
 * 되돌아섰나(터치), 매물대는 거기서 얼마나 오갔나(비중). 같은 자리를 둘이 함께 가리키면
 * 그것이 그 자리에 대한 두 개의 증거다.
 *
 * @param near 먼저 닿는 모서리
 * @param far 반대쪽 모서리
 * @param share 전체 거래량의 몇 %가 이 구간에서 오갔나. <b>두께를 기간과 무관하게 견주는 수다</b>
 * @param distancePercent 가까운 모서리까지 몇 %
 */
public record VolumeShelfReadout(
        Price near, Price far, Percentage share, Percentage distancePercent) {

    public VolumeShelfReadout {
        DomainValues.required(near, "가까운 모서리");
        DomainValues.required(far, "먼 모서리");
        DomainValues.required(share, "거래량 비중");
        DomainValues.required(distancePercent, "거리");
    }

    public static VolumeShelfReadout of(VolumeShelf shelf, Price close) {
        DomainValues.required(shelf, "매물대");
        BandReadout band = BandReadout.of(shelf.band(), close);
        return new VolumeShelfReadout(
                band.near(), band.far(), shelf.share(), band.distancePercent());
    }
}
