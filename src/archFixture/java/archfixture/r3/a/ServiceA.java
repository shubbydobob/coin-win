package archfixture.r3.a;

import archfixture.r3.b.ServiceB;

/** 규칙 3 위반: a → b → a 순환의 한쪽. */
public class ServiceA {
    public String callB() {
        return new ServiceB().name();
    }

    public String name() {
        return "a";
    }
}
