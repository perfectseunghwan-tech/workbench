package com.example.backend;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConnectionController {
    @GetMapping("/api/connection")
    public Map<String, String> connection() {
        return Map.of("status", "ok", "message", "백엔드 서버에 정상적으로 연결되었습니다.");
    }
}
