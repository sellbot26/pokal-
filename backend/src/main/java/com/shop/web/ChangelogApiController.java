package com.shop.web;

import com.shop.service.ChangelogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Changelog fürs Dashboard-Modal + manueller Update-Post in die Discord-Update-Channels. */
@RestController
@RequiredArgsConstructor
public class ChangelogApiController {

    private final ChangelogService changelog;

    @GetMapping("/api/changelog")
    public List<ChangelogService.Entry> all() {
        return changelog.all();
    }

    /** Owner-Button: neuesten Eintrag sofort in die konfigurierten Update-Channels posten. */
    @PostMapping("/api/admin/changelog/post")
    public Map<String, Object> post() {
        int sent = changelog.postLatest();
        if (sent == 0) throw new IllegalStateException(
                "No update channel configured or the bot can't write there — set the update channel ID in Settings → General first.");
        return Map.of("sent", sent);
    }
}
