package com.coinwin.ai.application.port.out;

import com.coinwin.ai.domain.DraftedFields;

/**
 * 문장에서 계획의 칸들을 읽어 온다.
 *
 * <p><b>읽어 온 것만 돌려준다.</b> 빠진 칸을 어떻게 할지는 이 포트의 관심사가 아니라
 * {@code DraftedFields} 의 규칙이다. 어댑터가 "없으면 기본값" 을 정하기 시작하면 그 정책이
 * 모델 제공자마다 흩어진다.
 */
public interface ExtractPlanPort {

    DraftedFields extractFrom(String sentence);
}
