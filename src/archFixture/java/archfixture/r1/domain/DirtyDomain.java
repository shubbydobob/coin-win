package archfixture.r1.domain;

import org.springframework.stereotype.Component;

/** 규칙 1 위반: domain 패키지가 Spring 에 의존한다. */
@Component
public class DirtyDomain {
    public int value() {
        return 1;
    }
}
