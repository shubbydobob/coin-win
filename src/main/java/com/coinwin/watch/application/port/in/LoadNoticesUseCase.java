package com.coinwin.watch.application.port.in;

import com.coinwin.watch.domain.Notice;
import java.util.List;

/** 거래소가 최근에 낸 공지. */
public interface LoadNoticesUseCase {

    List<Notice> recent();
}
