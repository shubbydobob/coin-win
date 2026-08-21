package archfixture.r3.b;

import archfixture.r3.a.ServiceA;

/** 규칙 3 위반: a → b → a 순환의 반대쪽. */
public class ServiceB {
    public String callA() {
        return new ServiceA().name();
    }

    public String name() {
        return "b";
    }
}
