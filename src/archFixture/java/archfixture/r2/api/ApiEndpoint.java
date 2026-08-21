package archfixture.r2.api;

/** 규칙 2 픽스처의 대상. domain 이 이 클래스를 참조하면 의존 방향이 뒤집힌다. */
public class ApiEndpoint {
    public String describe() {
        return "api";
    }
}
