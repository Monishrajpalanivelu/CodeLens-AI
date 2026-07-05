package com.codelens.ai.controller;

import com.codelens.ai.dto.SearchResultDto;
import com.codelens.ai.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public ResponseEntity<List<SearchResultDto>> search(
            @RequestParam Long repoId,
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int topK) {

        // Hard cap — prevent abuse / massive vector queries
        if (topK > 50)
            topK = 50;

        return ResponseEntity.ok(
                searchService.search(repoId, q, topK));
    }
}