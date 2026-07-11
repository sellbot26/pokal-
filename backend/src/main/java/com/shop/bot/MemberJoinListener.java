package com.shop.bot;

import com.shop.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * Auto-Role: Jeder, der dem Server beitritt, bekommt automatisch die konfigurierte Rolle.
 * Die Rollen-ID ist über Settings → General einstellbar (autoRoleId); greift nur in
 * Servern, in denen die Rolle tatsächlich existiert. Benötigt das aktivierte
 * "Server Members Intent" im Discord Developer Portal.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemberJoinListener extends ListenerAdapter {

    /** Standard-Rolle, solange im Dashboard nichts anderes gesetzt ist. */
    private static final String DEFAULT_AUTO_ROLE_ID = "1525245668824977518";

    private final SettingsService settings;

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if (event.getUser().isBot()) return;
        String roleId = settings.get("autoRoleId", DEFAULT_AUTO_ROLE_ID);
        if (roleId == null || roleId.isBlank()) return;
        Role role = event.getGuild().getRoleById(roleId.trim());
        if (role == null) return; // Rolle gehört zu einem anderen Server
        event.getGuild().addRoleToMember(event.getMember(), role).queue(
                ok -> log.info("Auto-Role '{}' an {} in '{}' vergeben",
                        role.getName(), event.getUser().getName(), event.getGuild().getName()),
                err -> log.warn("Auto-Role in '{}' fehlgeschlagen: {} (Bot-Rolle muss ÜBER der Auto-Rolle stehen)",
                        event.getGuild().getName(), err.getMessage()));
    }
}
