package com.coinwin.watch.application.port.out;

import com.coinwin.watch.domain.Notice;
import java.util.List;

/**
 * 거래소 공지를 읽어 오는 곳.
 *
 * <p>구현체가 둘이다 — 바이낸스와 인메모리. 인메모리가 필요한 이유가 특히 분명한 자리인데,
 * <b>이 엔드포인트는 문서화된 공개 API 가 아니라 바이낸스 웹사이트가 쓰는 것</b>이라 예고 없이
 * 바뀔 수 있다. 테스트가 실제 호출에 의존하면 남의 사정으로 빌드가 빨개진다.
 */
public interface LoadNoticesPort {

    List<Notice> recent(int limit);
}
