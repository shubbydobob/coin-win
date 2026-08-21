package com.coinwin.ai.domain;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 요약이 원본에 없는 수를 썼다.
 *
 * <p>503 인 이유는 <b>요약의 입력에 사용자의 자유 텍스트가 없기 때문</b>이다. 우리가 준 수치만
 * 넘겼는데 없는 수가 돌아왔다면 잘못한 것은 요청이 아니라 모델이고, 사용자가 고칠 것은 없다.
 * 다시 시도하면 되는 일이므로 {@link ExternalDataUnavailableException} 을 상속한다.
 * 계획 파싱({@code PlanNotUnderstoodException})이 422 인 것과 갈리는 지점이 여기다 —
 * 그쪽 입력에는 사용자가 쓴 문장이 있다.
 */
public class FabricatedNumberException extends ExternalDataUnavailableException {

    private static final long serialVersionUID = 1L;

    public FabricatedNumberException(List<BigDecimal> fabricated) {
        super("요약이 원본에 없는 수를 썼다: %s".formatted(fabricated.stream()
                .map(BigDecimal::toPlainString)
                .collect(Collectors.joining(", "))));
    }
}
