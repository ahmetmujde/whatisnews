package com.whatisnews.whatisnews.Controller;

import com.whatisnews.whatisnews.entity.FeedItem;
import com.whatisnews.whatisnews.entity.FeedSource;
import com.whatisnews.whatisnews.service.RssFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final RssFeedService service;

    // 1. Haberleri Getir
    // GET /api/news?page=0&size=10
    // İyileştirme: Sıralamayı (Sort) burada garanti altına aldık.
    @GetMapping
    public ResponseEntity<Page<FeedItem>> getNews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        // iOS uygulaması en yeni haberi en üstte görmek ister.
        // Bu yüzden "pubDate" alanına göre DESC (Azalan) sıralama ekledik.
        Pageable pageable = PageRequest.of(page, size, Sort.by("pubDate").descending());

        return ResponseEntity.ok(service.getAllNews(pageable));
    }

    // 2. RSS Taramasını Elle Tetikle
    // POST /api/news/refresh
    @PostMapping("/refresh")
    public ResponseEntity<String> refreshFeeds() {
        // Yeni servisteki loglama ve hata yönetimi sayesinde burası daha güvenli çalışır.
        service.fetchAndSaveAllFeeds();
        return ResponseEntity.ok("RSS taraması başarıyla tamamlandı.");
    }

    // 3. Yeni RSS Kaynağı Ekle
    // POST /api/news/source?name=BBC&url=http://...
    @PostMapping("/source")
    public ResponseEntity<FeedSource> addSource(
            @RequestParam String name,
            @RequestParam String url) {

        return ResponseEntity.ok(service.addSource(name, url));
    }
}