package com.coinwin.readout.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.VolumeProfile;
import java.util.Optional;

/**
 * 이 주기의 매물대가 지금 가격에 대해 말하는 것.
 *
 * <p><b>셋을 함께 낸다.</b> 위·아래만 내면 "매물대가 없다" 와 "지금 매물대 한가운데에 있다" 가
 * 화면에서 같은 모양이 된다 — 둘은 전혀 다른 상황이고, 뒤쪽은 <b>움직이려면 물린 물량을 지나야
 * 한다</b>는 뜻이다.
 *
 * <p><b>POC 는 언제나 있다.</b> 가장 두꺼운 칸은 거래가 한 건이라도 있으면 정해지므로 비어 있을
 * 수 없다. 매물대(위·아래·품은 것)는 평균보다 두꺼운 칸이 하나도 없으면 셋 다 빌 수 있고,
 * 그것은 거래량이 고르게 퍼져 있다는 뜻이다.
 *
 * @param pointOfControl 가장 두껍게 거래된 칸의 가운데. 흔히 POC 라 부른다
 * @param below 아래에서 가장 가까운 매물대
 * @param above 위에서 가장 가까운 매물대
 * @param here 지금 가격을 품고 있는 매물대
 */
public record VolumeProfileReadout(
        Price pointOfControl,
        Optional<VolumeShelfReadout> below,
        Optional<VolumeShelfReadout> above,
        Optional<VolumeShelfReadout> here) {

    public VolumeProfileReadout {
        DomainValues.required(pointOfControl, "POC");
        DomainValues.required(below, "아래 매물대");
        DomainValues.required(above, "위 매물대");
        DomainValues.required(here, "품고 있는 매물대");
    }

    public static VolumeProfileReadout of(VolumeProfile profile, Price close) {
        DomainValues.required(profile, "매물대");
        DomainValues.required(close, "현재가");
        return new VolumeProfileReadout(
                middleOf(profile),
                profile.nearestBelow(close).map(shelf -> VolumeShelfReadout.of(shelf, close)),
                profile.nearestAbove(close).map(shelf -> VolumeShelfReadout.of(shelf, close)),
                profile.containing(close).map(shelf -> VolumeShelfReadout.of(shelf, close)));
    }

    /**
     * POC 를 한 값으로 낸다.
     *
     * <p>칸에는 폭이 있지만 <b>여기서만은 한 점으로 줄인다</b> — 매물대와 달리 POC 는 "가장
     * 두꺼운 한 자리" 라는 뜻이고, 그 칸의 폭은 구간 수가 정한 격자이지 시장이 만든 경계가
     * 아니다. 매물대의 모서리는 반대로 시장이 만든 것이라 둘 다 낸다.
     */
    private static Price middleOf(VolumeProfile profile) {
        return profile.pointOfControl().band().middle();
    }
}
