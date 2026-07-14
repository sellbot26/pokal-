package com.shop.bot;

import com.shop.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto-Role: Jeder, der dem Server beitritt, bekommt automatisch die konfigurierte Rolle.
 *
 * Zwei Wege, damit es IMMER funktioniert:
 *  1) Join-Event (braucht das privilegierte "Server Members Intent" im Discord Developer Portal).
 *  2) Fallback ohne Intent: Bei jeder Bot-Interaktion (/shop, Buttons, …) wird die Rolle
 *     nachgezogen, falls sie fehlt — {@link #ensureAutoRole(Member)}.
 *
 * Settings → General: autoRoleId. Mehrere Rollen (auch aus verschiedenen Servern) können
 * kommagetrennt angegeben werden — pro Server greift die Rolle, die dort existiert.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemberJoinListener extends ListenerAdapter {

    /** Standard-Rolle, solange im Dashboard nichts anderes gesetzt ist. */
    private static final String DEFAULT_AUTO_ROLE_ID = "1525245668824977518";

    private final SettingsService settings;

    /** userId:guildId, für die die Rolle bereits sichergestellt wurde — verhindert API-Spam im Fallback. */
    private final Set<String> ensured = ConcurrentHashMap.newKeySet();

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if (event.getUser().isBot()) return;
        assignAutoRole(event.getMember(), "Join");
    }

    /**
     * Fallback ohne Members-Intent: von den Command-/Button-Handlers bei jeder Interaktion
     * aufgerufen. Vergibt die Auto-Rolle, falls der Member sie noch nicht hat.
     */
    public void ensureAutoRole(Member member) {
        if (member == null || member.getUser().isBot()) return;
        String key = member.getId() + ":" + member.getGuild().getId();
        if (!ensured.add(key)) return; // schon geprüft in dieser Laufzeit
        assignAutoRole(member, "Interaktion");
    }

    private void assignAutoRole(Member member, String trigger) {
        String configured = settings.get("autoRoleId", DEFAULT_AUTO_ROLE_ID);
        if (configured == null || configured.isBlank()) return;

        var guild = member.getGuild();
        Role role = null;
        // Mehrere IDs erlaubt (Komma/Leerzeichen) — es greift die Rolle, die in DIESEM Server existiert.
        for (String id : configured.split("[,\\s]+")) {
            if (id.isBlank()) continue;
            role = guild.getRoleById(id.trim());
            if (role != null) break;
        }
        if (role == null) {
            log.warn("Auto-Role: Keine der konfigurierten Rollen-IDs ({}) existiert in '{}' — "
                    + "Rollen-ID in Settings → General prüfen (Discord: Servereinstellungen → Rollen → Rechtsklick → ID kopieren).",
                    configured, guild.getName());
            return;
        }
        if (member.getRoles().contains(role)) return; // hat die Rolle schon

        var self = guild.getSelfMember();
        if (!self.hasPermission(Permission.MANAGE_ROLES)) {
            log.warn("Auto-Role in '{}': Bot fehlt die Berechtigung 'Rollen verwalten'.", guild.getName());
            return;
        }
        if (!self.canInteract(role)) {
            log.warn("Auto-Role in '{}': Bot-Rolle steht UNTER '{}' — in den Server-Einstellungen die "
                    + "Bot-Rolle über die Auto-Rolle ziehen.", guild.getName(), role.getName());
            return;
        }

        final Role r = role;
        guild.addRoleToMember(member, role).reason("Auto-Role (" + trigger + ")").queue(
                ok -> log.info("Auto-Role '{}' an {} in '{}' vergeben ({})",
                        r.getName(), member.getUser().getName(), guild.getName(), trigger),
                err -> log.warn("Auto-Role in '{}' fehlgeschlagen: {}", guild.getName(), err.getMessage()));
    }
}
