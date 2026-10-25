package com.riotanalizer.web;

import com.riotanalizer.dto.FetchQueueDto;
import com.riotanalizer.service.FetchQueueService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only view of the background fetch scheduler (also enforced in SecurityConfig). */
@RestController
@RequestMapping("/api")
public class FetchQueueController {

    private final FetchQueueService fetchQueue;

    public FetchQueueController(FetchQueueService fetchQueue) {
        this.fetchQueue = fetchQueue;
    }

    /** Who is being fetched now, who is next (in scheduler order) and who is due later. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/fetch-queue")
    public FetchQueueDto fetchQueue(@RequestParam(defaultValue = "15") int limit) {
        return fetchQueue.snapshot(limit);
    }
}
