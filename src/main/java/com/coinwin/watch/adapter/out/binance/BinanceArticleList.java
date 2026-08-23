package com.coinwin.watch.adapter.out.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 바이낸스 공지 목록 응답.
 *
 * <p>{@code data.catalogs[].articles[]} 로 두 겹 들어가 있다. 목록 하나를 얻는 데 두 겹인 것은
 * 이 API 가 <b>여러 분류를 한 번에 내려 주도록 만들어졌기</b> 때문이고, 우리는 그중 하나만
 * 쓴다.
 *
 * <p>{@code releaseDate} 는 epoch ms 다. 링크는 {@code code} 로 조립한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record BinanceArticleList(String code, Boolean success, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(List<Catalog> catalogs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Catalog(String catalogName, List<Article> articles) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Article(String code, String title, Long releaseDate) {
    }
}
