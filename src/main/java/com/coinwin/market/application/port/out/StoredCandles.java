package com.coinwin.market.application.port.out;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * 우리가 저장해 둔 캔들을 읽는 구현체.
 *
 * <p>{@link LoadCandlesPort} 구현체가 컨텍스트에 둘 이상 올라가므로 어느 쪽인지 밝혀야 한다.
 * 빈 이름({@code jdbcCandleAdapter})으로 주입하면 <b>어댑터의 클래스 이름이 application 층에
 * 박힌다.</b> 그러면 저장소를 바꿀 때 application 을 고쳐야 하고, 그것이 정확히 포트가
 * 막으려던 일이다. 애너테이션은 "무엇인지" 만 말하고 "누구인지" 는 말하지 않는다.
 */
@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
public @interface StoredCandles {
}
