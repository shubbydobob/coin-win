package com.coinwin.watch.application.service;

import com.coinwin.watch.application.port.in.LoadNoticesUseCase;
import com.coinwin.watch.application.port.out.LoadNoticesPort;
import com.coinwin.watch.domain.Notice;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 최근 공지를 몇 건 볼 것인가만 정한다.
 *
 * <p>그 수가 여기 있는 이유는 <b>화면의 사정이 아니라 정책</b>이기 때문이다. 화면이 정하면
 * 화면마다 다른 개수를 요청하게 되고, 그러면 거래소 호출량이 화면 배치에 따라 달라진다.
 */
@Service
public class NoticeService implements LoadNoticesUseCase {

    /** 이 화면은 "무슨 일이 있었나" 를 훑는 자리다. 스무 건이면 하루치가 넉넉히 들어온다. */
    private static final int RECENT_COUNT = 20;

    private final LoadNoticesPort port;

    public NoticeService(LoadNoticesPort port) {
        this.port = port;
    }

    @Override
    public List<Notice> recent() {
        return port.recent(RECENT_COUNT);
    }
}
