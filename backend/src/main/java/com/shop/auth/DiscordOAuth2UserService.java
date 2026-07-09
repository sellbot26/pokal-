package com.shop.auth;

import com.shop.model.ShopUser;
import com.shop.repo.ShopUserRepo;
import com.shop.service.GuildAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Login ausschließlich über Discord OAuth2. Admin-Rechte kommen aus der Discord-Rolle
 * (DISCORD_ADMIN_ROLE_ID) bzw. der ADMIN_IDS-Liste — kein separates Passwortsystem.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscordOAuth2UserService extends DefaultOAuth2UserService {

    private final ShopUserRepo userRepo;
    private final GuildAccessService guildAccess;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = super.loadUser(request);

        String id = oauthUser.getAttribute("id");
        String globalName = oauthUser.getAttribute("global_name");
        String username = globalName != null ? globalName : oauthUser.getAttribute("username");
        String avatarHash = oauthUser.getAttribute("avatar");
        String avatar = avatarHash != null
                ? "https://cdn.discordapp.com/avatars/" + id + "/" + avatarHash + ".png"
                : null;

        ShopUser user = userRepo.findById(id).orElseGet(() -> new ShopUser(id, username, avatar));
        if (user.isBanned()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("banned"), "Account gesperrt");
        }
        user.setUsername(username);
        user.setAvatar(avatar);
        user.setLastLogin(Instant.now());
        userRepo.save(user);

        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (guildAccess.isSiteAdmin(id)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return new DefaultOAuth2User(authorities, oauthUser.getAttributes(), "id");
    }
}
