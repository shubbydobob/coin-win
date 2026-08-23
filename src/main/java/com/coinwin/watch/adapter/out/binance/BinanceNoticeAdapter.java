package com.coinwin.watch.adapter.out.binance;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.watch.application.port.out.LoadNoticesPort;
import com.coinwin.watch.domain.Notice;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 바이낸스 공지를 읽는다. 키가 필요 없다.
 *
 * <p><b>이 엔드포인트는 문서화된 공개 API 가 아니다.</b> 바이낸스 웹사이트가 쓰는 것이라 예고
 * 없이 바뀔 수 있다. 그래서 둘을 지킨다 — 계약 테스트를 인메모리로 돌리고, 이 블록의 실패가
 * 다른 블록을 죽이지 않게 <b>엔드포인트를 캘린더와 따로</b> 둔다.
 *
 * <p>시각은 거래소가 준 {@code releaseDate}(epoch ms) 를 쓴다. 우리 시계로 찍지 않는다.
 */
@Component
public class BinanceNoticeAdapter implements LoadNoticesPort {

    private static final String ARTICLES = "/bapi/apex/v1/public/apex/cms/article/list/query";

    /** 선물·현물 공지가 함께 오는 분류. 웹사이트가 "New Cryptocurrency Listing" 으로 부른다. */
    private static final String CATALOG_ID = "48";

    private static final String ARTICLE_URL = "https://www.binance.com/support/announcement/%s";

    private final RestClient client;

    public BinanceNoticeAdapter(RestClient binanceCmsRestClient) {
        this.client = binanceCmsRestClient;
    }

    @Override
    public List<Notice> recent(int limit) {
        BinanceArticleList response = fetch(limit);
        if (response == null || !Boolean.TRUE.equals(response.success()) || response.data() == null) {
            throw new ExternalDataUnavailableException("바이낸스 공지를 가져오지 못했다", null);
        }
        return response.data().catalogs().stream()
                .filter(catalog -> catalog.articles() != null)
                .flatMap(catalog -> catalog.articles().stream())
                .map(BinanceNoticeAdapter::toNotice)
                .sorted(Comparator.comparing(Notice::at).reversed())
                .limit(limit)
                .toList();
    }

    private static Notice toNotice(BinanceArticleList.Article article) {
        return new Notice(
                article.title(),
                Instant.ofEpochMilli(article.releaseDate()),
                ARTICLE_URL.formatted(article.code()));
    }

    private BinanceArticleList fetch(int limit) {
        try {
            return client.get()
                    .uri(uri -> uri.path(ARTICLES)
                            .queryParam("type", 1)
                            .queryParam("catalogId", CATALOG_ID)
                            .queryParam("pageNo", 1)
                            .queryParam("pageSize", limit)
                            .build())
                    .retrieve()
                    .body(BinanceArticleList.class);
        } catch (RestClientException e) {
            throw new ExternalDataUnavailableException("바이낸스 공지를 가져오지 못했다", e);
        }
    }
}
