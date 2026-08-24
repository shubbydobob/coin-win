package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Quantity;

/**
 * 가격 구간 하나와 거기서 오간 거래량.
 *
 * <p>매물대의 가장 작은 단위다. 이것들을 이어 붙인 것이 {@link VolumeShelf} 이고, 가장 두꺼운
 * 하나가 {@code VolumeProfile.pointOfControl()} 이다.
 */
public record VolumeBin(PriceBand band, Quantity volume) {

    public VolumeBin {
        DomainValues.required(band, "가격 구간");
        DomainValues.required(volume, "거래량");
    }
}
