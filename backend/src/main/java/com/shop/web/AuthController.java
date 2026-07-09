package com.shop.web;

import com.shop.repo.ShopUserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final ShopUserRepo userRepo;

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal OAuth2User principal, Authentication auth) {
        String id = principal.getAttribute("id");
        boolean admin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("admin", admin);
        userRepo.findById(id).ifPresent(u -> {
            result.put("username", u.getUsername());
            result.put("avatar", u.getAvatar());
        });
        return result;
    }
}
