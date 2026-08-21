package com.coinwin.market.application.port.out;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * 거래소에서 직접 캔들을 받아 오는 구현체. 느리고, 네트워크에 기대고, 저장하지 않는다.
 *
 * @see StoredCandles 왜 빈 이름 대신 애너테이션인지
 */
@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
public @interface ExchangeCandles {
}
