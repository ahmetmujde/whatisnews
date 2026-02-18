package com.whatisnews.whatisnews.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.whatisnews.whatisnews.entity.FeedItem;
import com.whatisnews.whatisnews.entity.FeedSource;
import com.whatisnews.whatisnews.repository.FeedItemRepository;
import com.whatisnews.whatisnews.repository.FeedSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RssFeedService {

    private final FeedItemRepository itemRepository;
    private final FeedSourceRepository sourceRepository;

    // 1. Haberleri Listeleme Metodu (API bunu çağıracak)
    public Page<FeedItem> getAllNews(Pageable pageable) {
        return itemRepository.findAllByOrderByPubDateDesc(pageable);
    }

    // --- 2. RSS Kaynak Ekleme (Controller ile uyumlu) ---
    public FeedSource addSource(String name, String url) {
        FeedSource source = new FeedSource();
        source.setName(name);
        source.setUrl(url);
        source.setLastChecked(LocalDateTime.now());
        return sourceRepository.save(source);
    }

    @Scheduled(cron = "0 0 7-23,0 * * *")
    public void fetchAndSaveAllFeeds() {
        log.info("RSS taraması başlatılıyor...");
        List<FeedSource> sources = sourceRepository.findAll();

        for (FeedSource source : sources) {
            try {
                // 1. Adım: AĞ İSTEĞİ (Transaction dışında yapılır)
                // Veritabanını meşgul etmeden veriyi çekiyoruz
                URL feedUrl = new URL(source.getUrl());
                SyndFeedInput input = new SyndFeedInput();
                SyndFeed feed = input.build(new XmlReader(feedUrl));

                // 2. Adım: VERİ İŞLEME VE KAYDETME (Transaction içinde)
                processAndSaveFeed(source, feed);

            } catch (Exception e) {
                log.error("Hata oluştu - Kaynak: {} - Hata: {}", source.getName(), e.getMessage());
            }
        }
        log.info("RSS taraması tamamlandı.");
    }

    // Bu metot bir "Transaction" başlatır.
    // Yani ya hepsini kaydeder ya da hata olursa geri alır.
    @Transactional
    protected void processAndSaveFeed(FeedSource source, SyndFeed feed) {
        // Feed içindeki tüm haber linklerini listeye çevir
        List<String> feedLinks = feed.getEntries().stream()
                .map(SyndEntry::getLink)
                .collect(Collectors.toList());

        if (feedLinks.isEmpty()) return;

        // DB SORGUSU: Bu linklerden hangileri veritabanında ZATEN VAR?
        // Tek bir SQL sorgusu ile kontrol ediyoruz (Performans burada artıyor)
        Set<String> existingLinks = itemRepository.findExistingLinks(feedLinks);

        List<FeedItem> newItems = new ArrayList<>();

        for (SyndEntry entry : feed.getEntries()) {
            // Eğer link veritabanında yoksa, listeye ekle
            if (!existingLinks.contains(entry.getLink())) {
                FeedItem item = mapEntryToItem(entry, source);
                newItems.add(item);
            }
        }

        // Toplu Kayıt (Batch Insert)
        if (!newItems.isEmpty()) {
            itemRepository.saveAll(newItems);
            log.info("{} kaynağından {} yeni haber eklendi.", source.getName(), newItems.size());

            // Kaynağın son kontrol edilme zamanını güncelle
            source.setLastChecked(LocalDateTime.now());
            sourceRepository.save(source);
        }
    }

    // Helper Metot: RSS Entry nesnesini bizim Entity'e çevirir
    private FeedItem mapEntryToItem(SyndEntry entry, FeedSource source) {
        FeedItem item = new FeedItem();
        item.setTitle(cleanText(entry.getTitle()));
        item.setLink(entry.getLink());
        item.setSource(source);

        // HTML Temizliği (iOS'te düzgün görünmesi için)
        String rawDescription = entry.getDescription() != null ? entry.getDescription().getValue() : "";
        item.setDescription(cleanHtml(rawDescription));

        // Tarih Dönüşümü
        Date publishedDate = entry.getPublishedDate();
        if (publishedDate == null) {
            publishedDate = new Date();
        }
        item.setPubDate(publishedDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime());

        return item;
    }

    // Jsoup ile HTML taglerini temizleyen metot
    private String cleanHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        // Sadece text'i alır, <p>, <br>, <img> hepsini siler.
        // Eğer özetin sonuna "..." eklemek istersen burayı geliştirebilirsin.
        return Jsoup.parse(html).text();
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text.trim();
    }

}