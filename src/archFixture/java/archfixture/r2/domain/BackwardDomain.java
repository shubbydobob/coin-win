package archfixture.r2.domain;

import archfixture.r2.api.ApiEndpoint;

/** 규칙 2 위반: domain 이 api 를 참조한다 (의존 방향 역행). */
public class BackwardDomain {
    private final ApiEndpoint endpoint = new ApiEndpoint();

    public String leak() {
        return endpoint.describe();
    }
}
