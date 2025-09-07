package com.example.keelungapi.controller;

import com.example.keelungapi.crawler.KeelungSightsCrawler;
import com.example.keelungapi.models.Sight;
import com.example.keelungapi.repository.SightRepository;
import com.example.keelungapi.service.SightService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.annotation.PostConstruct;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
public class SightController {

    private final KeelungSightsCrawler crawler;
    private final SightRepository sightRepository;
    private final SightService sightService;
    private static final Logger logger = LoggerFactory.getLogger(SightController.class);
    private static final String[] ZONES = {"中正", "仁愛", "信義", "中山", "安樂", "暖暖", "七堵"};

    public SightController(KeelungSightsCrawler crawler, SightRepository sightRepository, SightService sightService) {
        this.crawler = crawler;
        this.sightRepository = sightRepository;
        this.sightService = sightService;
    }

    @PostConstruct
    public void initializeDatabase() {
        logger.info("應用程式啟動，開始自動爬取所有景點資料...");
        try {
            // 檢查資料庫是否為空
            if (sightRepository.count() > 0) {
                logger.info("資料庫中已有資料，跳過初始化");
                return;
            }

            int totalSights = 0;
            for (String zone : ZONES) {
                try {
                    Sight[] sights = crawler.getItems(zone);
                    for (Sight sight : sights) {
                        try {
                            sight.setId(null);
                            sightRepository.save(sight);
                            totalSights++;
                            logger.info("成功保存景點: {} ({}區)", sight.getSightName(), zone);
                        } catch (Exception e) {
                            logger.error("保存景點時發生錯誤 {}: {}", sight.getSightName(), e.getMessage());
                        }
                    }
                    logger.info("完成 {} 區的爬取，共 {} 個景點", zone, sights.length);
                } catch (Exception e) {
                    logger.error("爬取 {} 區景點時發生錯誤: {}", zone, e.getMessage());
                }
            }
            logger.info("初始化完成，共保存了 {} 個景點", totalSights);
        } catch (Exception e) {
            logger.error("初始化資料庫時發生錯誤: {}", e.getMessage());
        }
    }

    @GetMapping("/SightAPI")
    public List<Sight> getSightsByWeb(@RequestParam String zone) {
        Sight[] sights = crawler.getItems(zone);
        logger.info("從網站爬取資料 zone={}, sights={}", zone, java.util.Arrays.toString(sights));
        if (sights.length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "查無資料");
        }
        return List.of(sights);
    }

    @GetMapping("/SightAPI/old")
    public List<Sight> getSights(@RequestParam String zone) {
        Sight[] sights = crawler.getItems(zone);
        logger.info("zone={}, sights={}", zone, java.util.Arrays.toString(sights));
        if (sights.length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "查無資料");
        }
        return List.of(sights);
    }

    @GetMapping("/SightAPI/db")
    public List<Sight> getSightsFromDB(@RequestParam String zone) {
        logger.info("從資料庫查詢 {} 區的景點", zone);
        return sightService.getSightsByZone(zone);
    }

    @PostMapping("/SightAPI/crawl-all")
    public ResponseEntity<String> crawlAllSights() {
        try {
            int totalSights = 0;
            // 先清空现有数据
            sightRepository.deleteAll();

            for (String zone : ZONES) {
                try {
                    Sight[] sights = crawler.getItems(zone);
                    for (Sight sight : sights) {
                        try {
                            sight.setId(null);  // 确保创建新记录
                            sightRepository.insert(sight);
                            totalSights++;
                            logger.info("成功保存景點: {}", sight.getSightName());
                        } catch (Exception e) {
                            logger.error("保存景點時發生錯誤: {} - {}", sight.getSightName(), e.getMessage());
                        }
                    }
                    logger.info("已爬取並保存 {} 區 {} 個景點", zone, sights.length);
                } catch (Exception e) {
                    logger.error("爬取 {} 區景點時發生錯誤: {}", zone, e.getMessage());
                }
            }
            return ResponseEntity.ok("成功爬取並保存了 " + totalSights + " 個景點");
        } catch (Exception e) {
            logger.error("執行全部爬取時發生錯誤: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "執行爬取時發生錯誤: " + e.getMessage());
        }
    }

    @PostMapping("/SightAPI")
    public ResponseEntity<Void> createSight(@RequestBody Sight sight) {
        sight.setId(null);
        sightRepository.insert(sight);

        URI uri = ServletUriComponentsBuilder
                .fromCurrentRequestUri()
                .path("/{id}")
                .build(Map.of("id", sight.getId()));

        return ResponseEntity.created(uri).build();
    }

    @GetMapping("/SightAPI/{id}")
    public ResponseEntity<Sight> getSight(@PathVariable String id) {
        Optional<Sight> sightOp = sightRepository.findById(id);
        return sightOp.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/SightAPI/ids")
    public ResponseEntity<List<Sight>> getSights(@RequestParam List<String> idList) {
        List<Sight> sights = sightRepository.findAllById(idList);
        return ResponseEntity.ok(sights);
    }
}
