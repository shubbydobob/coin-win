package com.coinwin.ai.domain;

import com.coinwin.common.domain.DomainException;

/**
 * 모델의 답을 계획의 칸으로 읽어낼 수 없었다. 칸이 빈 것이 아니라 <b>답의 형식이 깨진</b> 경우다.
 *
 * <p>{@link IncompletePlanException} 과 나누는 이유는 사용자가 할 일이 다르기 때문이다.
 * 빈 칸은 문장에 그 값을 넣어 다시 요청하면 되지만, 형식이 깨진 답은 사용자가 고칠 수 있는
 * 것이 아니라 그냥 다시 시도할 일이다.
 */
public class PlanNotUnderstoodException extends DomainException {

    private static final long serialVersionUID = 1L;

    public PlanNotUnderstoodException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
