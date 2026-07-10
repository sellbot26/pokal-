package com.shop.web;

import com.shop.model.ShopUser;
import com.shop.repo.ShopUserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Eigener Titel + Nachricht für die Liefer-DM pro Verkäufer. */
@RestController
@RequiredArgsConstructor
public class DeliveryConfigController {

    public record DeliveryRequest(String title, String message) {}

    private final ShopUserRepo userRepo;
    private final com.shop.service.PlanService planService;
    private final com.shop.service.GuildAccessService guildAccess;

    @GetMapping("/api/my/delivery-config")
    public Map<String, String> get(@AuthenticationPrincipal OAuth2User principal) {
        ShopUser user = userRepo.findById(principal.<String>getAttribute("id")).orElseThrow();
        return Map.of(
                "title", user.getDeliveryTitle() == null ? "" : user.getDeliveryTitle(),
                "message", user.getDeliveryMessage() == null ? "" : user.getDeliveryMessage()
        );
    }

    @PutMapping("/api/my/delivery-config")
    public Map<String, String> update(@RequestBody DeliveryRequest req, @AuthenticationPrincipal OAuth2User principal) {
        String uid = principal.getAttribute("id");
        ShopUser user = userRepo.findById(uid).orElseThrow();
        if (!guildAccess.isSiteAdmin(uid) && !planService.isAtLeast(user, "PRO")) {
            throw new IllegalStateException("A custom delivery message is a Pro feature. Upgrade your plan to edit it.");
        }
        if (req.title() != null) user.setDeliveryTitle(req.title().isBlank() ? null : req.title().trim());
        if (req.message() != null) user.setDeliveryMessage(req.message().isBlank() ? null : req.message());
        userRepo.save(user);
        return get(principal);
    }
}
