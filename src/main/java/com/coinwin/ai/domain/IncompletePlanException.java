package com.coinwin.ai.domain;

import com.coinwin.common.domain.DomainException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 문장에서 계획의 어떤 칸을 읽어내지 못했다.
 *
 * <p>빠진 칸을 <b>전부</b> 들고 다닌다. 하나씩 되묻게 만들면 사용자가 같은 문장을 다섯 번
 * 고쳐 쓰게 된다.
 *
 * <p>이것이 규칙 위반(422)인 이유는 값 하나가 잘못된 것이 아니라 <b>계획으로 성립하지 않는</b>
 * 상태이기 때문이다. 잘못 채운 진입가는 잘못된 포지션 사이즈로 이어지고 그 오류는 계산 계층
 * 전체를 조용히 오염시킨다 — 비는 것보다 틀린 것이 나쁘다.
 */
public class IncompletePlanException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final transient List<PlanField> missing;

    public IncompletePlanException(List<PlanField> missing) {
        super("문장에서 읽어내지 못한 항목이 있다: %s. 추측해서 채우지 않으므로 그 값을 문장에 넣어 다시 요청한다."
                .formatted(missing.stream().map(PlanField::label).collect(Collectors.joining(", "))));
        this.missing = List.copyOf(missing);
    }

    public List<PlanField> missing() {
        return missing;
    }
}
