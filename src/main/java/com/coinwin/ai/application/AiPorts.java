package com.coinwin.ai.application;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import java.util.Optional;

/**
 * 키가 없으면 AI 어댑터 빈이 아예 없다. 그것을 요청 시점에 503 으로 바꾸는 한 자리.
 *
 * <p>서비스가 포트를 {@code Optional} 로 받는 이유가 여기 있다. 필수로 받으면 키가 없는 순간
 * 서비스 빈도 못 만들고, 그러면 컨트롤러도 없어져 <b>엔드포인트 자체가 사라진다.</b> 문서에는
 * 있는데 404 가 나는 것보다 "설정되지 않았다" 는 503 이 낫다.
 */
public final class AiPorts {

    private AiPorts() {
    }

    public static <T> T configured(Optional<T> port) {
        return port.orElseThrow(() -> new ExternalDataUnavailableException(
                "AI 기능이 설정되지 않았다. OPENAI_API_KEY 환경변수가 필요하다"));
    }
}
