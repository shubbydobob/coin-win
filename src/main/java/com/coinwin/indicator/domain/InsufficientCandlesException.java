package com.coinwin.indicator.domain;

/**
 * 워밍업 구간을 채울 만큼 캔들이 없을 때 던진다.
 *
 * <p>일반적인 지표 오류와 타입을 나눈 이유는 <b>고칠 방법이 다르기</b> 때문이다. 설정이 틀린
 * 것이 아니라 데이터가 모자란 것이므로, 부르는 쪽의 대응은 "캔들을 더 받아 온다" 하나뿐이다.
 * 필요한 개수를 메시지에 실어 얼마나 더 받아야 하는지 바로 알 수 있게 한다.
 */
public class InsufficientCandlesException extends InvalidIndicatorException {

    private static final long serialVersionUID = 1L;

    public InsufficientCandlesException(String indicator, int required, int actual) {
        super("%s 계산에는 캔들이 %d 개 필요하다: %d 개뿐이다".formatted(indicator, required, actual));
    }
}
