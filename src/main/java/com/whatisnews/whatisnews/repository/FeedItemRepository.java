package com.whatisnews.whatisnews.repository;

import com.whatisnews.whatisnews.entity.FeedItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface FeedItemRepository extends JpaRepository<FeedItem, Long> {

    boolean existsByLink(String link);

    Page<FeedItem> findAllByOrderByPubDateDesc(Pageable pageable);

    @Query("SELECT f.link FROM FeedItem f WHERE f.link IN :links")
    Set<String> findExistingLinks(@Param("links") List<String> links);
}
