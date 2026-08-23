package com.coinwin.watch.application.port.in;

import com.coinwin.watch.domain.EventCalendar;

/** 무엇이 예정돼 있나. */
public interface LoadCalendarUseCase {

    EventCalendar calendar();
}
