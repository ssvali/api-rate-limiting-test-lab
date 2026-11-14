package org.qaspark.ratelimit.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ApiController {

    @GetMapping("/api/resource")
    public Map<String, String> getResource() {

        return Map.of(
                "message", "API request successful"
        );
    }
}