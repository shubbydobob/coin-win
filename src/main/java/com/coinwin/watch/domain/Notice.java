package com.coinwin.watch.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import java.time.Instant;

/**
 * 거래소가 낸 공지 하나.
 *
 * <p><b>분류하지 않는다.</b> "이 공고는 호재" 같은 판정은 이 프로젝트가 하지 않기로 한
 * 일이다({@code scope.md}). 담는 것은 제목·시각·링크 셋뿐이고 본문도 가져오지 않는다.
 *
 * <p><b>이것은 매크로 뉴스가 아니다.</b> 상장·상장폐지·점검 같은 거래소 자체 소식이다.
 * 재무부 발표 같은 것은 여기 걸리지 않고, 그쪽은 {@link ScheduledEvent} 가 담당한다.
 * 그 구분이 {@code scope.md} 의 실시간 뉴스 금지를 그대로 살아 있게 한다.
 */
public record Notice(String title, Instant at, String url) {

    public Notice {
        DomainValues.required(title, "공고 제목");
        DomainValues.required(at, "공고 시각");
        DomainValues.required(url, "공고 링크");
        if (title.isBlank()) {
            throw new InvalidValueException("공고 제목은 비어 있을 수 없다");
        }
    }
}
