package com.coinwin.journal.adapter.out.persistence;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QueryDSL 질의 팩토리를 만든다. 어댑터 안에 두는 이유는 <b>QueryDSL 이 저장 기술이기
 * 때문</b>이다 — application 층은 이 타입의 존재를 알 필요가 없고, 알면 규칙 4 위반이다.
 */
@Configuration
class JournalPersistenceConfig {

    @Bean
    JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}
