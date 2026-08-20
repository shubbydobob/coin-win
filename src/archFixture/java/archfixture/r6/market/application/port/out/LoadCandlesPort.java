package archfixture.r6.market.application.port.out;

/** 규칙 6 픽스처의 대상 포트. OrphanAdapter 는 이것을 구현하지 않는다. */
public interface LoadCandlesPort {
    String loadCandles();
}
