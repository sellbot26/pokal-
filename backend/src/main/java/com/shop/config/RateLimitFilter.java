package com.shop.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Einfaches Sliding-Window-Rate-Limit pro IP auf alle /api-Routen. */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int LIMIT_PER_MINUTE = 120;
    private static final long WINDOW_MS = 60_000;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        String ip = request.getRemoteAddr();
        long now = System.currentTimeMillis();
        Deque<Long> window = hits.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
            window.pollFirst();
        }
        if (window.size() >= LIMIT_PER_MINUTE) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Zu viele Anfragen, bitte kurz warten.\"}");
            return;
        }
        window.addLast(now);
        if (hits.size() > 10_000) {
            hits.clear(); // grober Schutz gegen Speicherwachstum
        }
        chain.doFilter(request, response);
    }
}
