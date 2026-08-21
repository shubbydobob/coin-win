package com.coinwin.ai.application.port.in;

import com.coinwin.ai.domain.JournalAnswer;

/**
 * 매매 기록에 자연어로 묻는다.
 *
 * <p>답은 <b>이미 일어난 일</b>에 대해서만 한다. 앞으로 무엇을 사야 하는지, 지금 들어가도
 * 되는지는 이 인터페이스가 답하는 질문이 아니다({@code docs/adr/005}).
 */
public interface AskJournalUseCase {

    JournalAnswer ask(String question, int topK);
}
