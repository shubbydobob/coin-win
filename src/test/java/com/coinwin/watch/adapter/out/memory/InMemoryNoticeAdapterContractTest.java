package com.coinwin.watch.adapter.out.memory;

import com.coinwin.watch.application.port.out.LoadNoticesPort;
import com.coinwin.watch.application.port.out.NoticePortContract;
import java.time.Instant;

/** 인메모리 어댑터가 공지 포트의 계약을 지키는가. */
class InMemoryNoticeAdapterContractTest extends NoticePortContract {

    @Override
    protected LoadNoticesPort port() {
        return InMemoryNoticeAdapter.withSample(Instant.parse("2026-08-23T09:00:00Z"));
    }
}
