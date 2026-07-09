package com.shop.service;

import com.shop.bot.JdaHolder;
import com.shop.config.ShopProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.Permission;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Klärt zwei Fragen für die Multi-Tenant-Trennung:
 *  1. Ist dieser Discord-Nutzer der Site-Betreiber (sieht/verwaltet alles)?
 *  2. Welche Discord-Server darf dieser Nutzer als "sein eigener Shop" verwalten
 *     (weil er dort Owner ist oder Administrator-Rechte hat)?
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GuildAccessService {

    private final ShopProperties props;
    private final JdaHolder jda;

    /** Site-Betreiber: nur wer explizit in ADMIN_IDS steht (oder die konfigurierte Admin-Rolle hat). */
    public boolean isSiteAdmin(String discordId) {
        if (props.isOpenDashboard()) return true;
        if (props.getDiscord().adminIdList().contains(discordId)) return true;

        String roleId = props.getDiscord().getAdminRoleId();
        String guildId = props.getDiscord().getGuildId();
        if (roleId == null || roleId.isBlank() || guildId == null || guildId.isBlank() || !jda.isReady()) {
            return false;
        }
        try {
            Guild guild = jda.get().getGuildById(guildId);
            if (guild == null) return false;
            Member member = guild.retrieveMemberById(discordId).complete();
            return member != null && member.getRoles().stream().anyMatch(r -> r.getId().equals(roleId.trim()));
        } catch (Exception e) {
            log.warn("Site-Admin-Check fehlgeschlagen für {}: {}", discordId, e.getMessage());
            return false;
        }
    }

    /** Server, auf denen dieser Nutzer Owner ist oder Administrator-Rechte hat — sein eigener Tenant-Bereich. */
    public Set<String> managedGuildIds(String discordId) {
        Set<String> result = new LinkedHashSet<>();
        if (!jda.isReady()) return result;
        for (Guild guild : jda.get().getGuilds()) {
            try {
                if (String.valueOf(guild.getOwnerIdLong()).equals(discordId)) {
                    result.add(guild.getId());
                    continue;
                }
                Member member = guild.retrieveMemberById(discordId).complete();
                if (member != null && member.hasPermission(Permission.ADMINISTRATOR)) {
                    result.add(guild.getId());
                }
            } catch (Exception e) {
                // Nutzer ist auf diesem Server evtl. nicht Mitglied — einfach überspringen
            }
        }
        return result;
    }

    public boolean manages(String discordId, String guildId) {
        if (guildId == null || guildId.isBlank()) return false;
        return managedGuildIds(discordId).contains(guildId);
    }
}
