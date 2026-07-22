package com.shop.web;

import com.shop.model.ChatMessage;
import com.shop.model.ChatUser;
import com.shop.repo.ChatMessageRepo;
import com.shop.repo.ChatUserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Backend für die SecureChat-Desktop-App (C++/ImGui).
 *
 * Bewusst isoliert unter {@code /api/chat/**} und mit eigenem Token-Login —
 * unabhängig vom Discord-Login des Shops. Nachrichten werden nur als
 * <b>Ciphertext</b> gespeichert und weitergereicht (Ende-zu-Ende, der Server
 * kann nicht mitlesen). Die Authentifizierung schützt die Konten und stellt
 * sicher, dass niemand fremde Nachrichten abholt.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatApiController {

    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,32}$");
    private static final int ONLINE_WINDOW_SECONDS = 40;

    private final ChatUserRepo users;
    private final ChatMessageRepo messages;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // ===================== Requests =====================

    public record RegisterRequest(String username, String password, String publicKey) {}
    public record LoginRequest(String username, String password) {}
    public record SendRequest(String to, String ciphertext) {}
    public record KeyUpdateRequest(String publicKey) {}

    /** Health-/Versions-Marker (ohne Auth) — zum Prüfen, welcher Stand live ist + CORS-Test. */
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("pong", true, "build", "cors-mvc-1");
    }

    // ===================== Register / Login =====================

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterRequest req) {
        String username = req.username() == null ? "" : req.username().trim();
        if (!USERNAME.matcher(username).matches()) {
            throw bad("Username: 3–32 Zeichen, nur Buchstaben, Zahlen, _");
        }
        if (req.password() == null || req.password().length() < 6) {
            throw bad("Passwort muss mindestens 6 Zeichen haben.");
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            throw bad("Username ist bereits vergeben.");
        }
        ChatUser u = new ChatUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setPublicKey(req.publicKey());
        u.setToken(newToken());
        u.setLastSeen(Instant.now());
        users.save(u);
        return session(u);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        String username = req.username() == null ? "" : req.username().trim();
        ChatUser u = users.findByUsernameIgnoreCase(username)
                .filter(x -> req.password() != null && encoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Falscher Username oder Passwort."));
        u.setToken(newToken());
        u.setLastSeen(Instant.now());
        users.save(u);
        return session(u);
    }

    /** Öffentlichen Schlüssel nachträglich setzen/aktualisieren (z. B. nach Neuinstallation). */
    @PostMapping("/key")
    public Map<String, Object> updateKey(@RequestHeader("X-Chat-Token") String token,
                                         @RequestBody KeyUpdateRequest req) {
        ChatUser me = auth(token);
        me.setPublicKey(req.publicKey());
        users.save(me);
        return Map.of("ok", true);
    }

    // ===================== Kontakte =====================

    /** Alle anderen Nutzer (zum Auswählen eines Chat-Partners). */
    @GetMapping("/users")
    public List<Map<String, Object>> listUsers(@RequestHeader("X-Chat-Token") String token) {
        ChatUser me = auth(token);
        List<Map<String, Object>> out = new ArrayList<>();
        Instant now = Instant.now();
        for (ChatUser u : users.findAllByOrderByUsernameAsc()) {
            if (u.getId().equals(me.getId())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", u.getUsername());
            m.put("hasKey", u.getPublicKey() != null && !u.getPublicKey().isBlank());
            m.put("online", u.getLastSeen() != null
                    && u.getLastSeen().isAfter(now.minus(ONLINE_WINDOW_SECONDS, ChronoUnit.SECONDS)));
            out.add(m);
        }
        return out;
    }

    /** Öffentlicher Schlüssel eines Nutzers — zum Ableiten des gemeinsamen Geheimnisses. */
    @GetMapping("/key/{username}")
    public Map<String, Object> getKey(@RequestHeader("X-Chat-Token") String token,
                                      @PathVariable String username) {
        auth(token);
        ChatUser u = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nutzer nicht gefunden."));
        return Map.of("username", u.getUsername(),
                "publicKey", u.getPublicKey() == null ? "" : u.getPublicKey());
    }

    // ===================== Nachrichten =====================

    @PostMapping("/send")
    public Map<String, Object> send(@RequestHeader("X-Chat-Token") String token,
                                    @RequestBody SendRequest req) {
        ChatUser me = auth(token);
        String to = req.to() == null ? "" : req.to().trim();
        ChatUser recipient = users.findByUsernameIgnoreCase(to)
                .orElseThrow(() -> bad("Empfänger existiert nicht."));
        if (req.ciphertext() == null || req.ciphertext().isBlank()) {
            throw bad("Leere Nachricht.");
        }
        // Bis ~8 MB Base64 -> erlaubt verschlüsselte Datei-/Bildanhänge (~5–6 MB Datei).
        if (req.ciphertext().length() > 8_000_000) {
            throw bad("Anhang zu groß (max. ca. 5 MB).");
        }
        ChatMessage m = new ChatMessage();
        m.setFromUser(me.getUsername());
        m.setToUser(recipient.getUsername());
        m.setCiphertext(req.ciphertext());
        messages.save(m);
        return Map.of("ok", true, "id", m.getId());
    }

    /** Neue an mich gerichtete Nachrichten seit {@code since} (ID). Aktualisiert "online". */
    @GetMapping("/poll")
    public Map<String, Object> poll(@RequestHeader("X-Chat-Token") String token,
                                    @RequestParam(defaultValue = "0") long since) {
        ChatUser me = auth(token);
        me.setLastSeen(Instant.now());
        users.save(me);

        List<ChatMessage> incoming = messages.findByToUserAndIdGreaterThanOrderByIdAsc(me.getUsername(), since);
        List<Map<String, Object>> list = new ArrayList<>();
        long last = since;
        for (ChatMessage m : incoming) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", m.getId());
            dto.put("from", m.getFromUser());
            dto.put("ciphertext", m.getCiphertext());
            dto.put("ts", m.getCreatedAt().toEpochMilli());
            list.add(dto);
            last = m.getId();
        }
        return Map.of("messages", list, "last", last);
    }

    // ===================== Helfer =====================

    private Map<String, Object> session(ChatUser u) {
        return Map.of("token", u.getToken(), "username", u.getUsername());
    }

    private ChatUser auth(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Kein Token.");
        }
        return users.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ungültiges Token — bitte neu einloggen."));
    }

    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    /**
     * Fehler der Chat-Endpunkte als lesbares JSON {"error": "..."} ausliefern —
     * sonst zeigt Spring nur ein generisches "Bad Request" ohne den echten Grund.
     * Gilt nur für diesen Controller, ändert nichts am restlichen Shop.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handle(ResponseStatusException ex) {
        String reason = ex.getReason() == null ? "Fehler" : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", reason));
    }
}
