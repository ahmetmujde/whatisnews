package com.whatisnews.whatisnews.repository;

import com.whatisnews.whatisnews.entity.FeedSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedSourceRepository  extends JpaRepository<FeedSource, Long> {
}
