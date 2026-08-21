package com.coinwin.journal.domain;

import com.coinwin.common.domain.NotFoundException;

/** 식별자로 지목한 거래가 없다. 404 가 된다. */
public class TradeNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    public TradeNotFoundException(TradeId id) {
        super("그런 거래가 없다: " + id);
    }
}
