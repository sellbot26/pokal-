package com.shop.bot;

import net.dv8tion.jda.api.JDA;
import org.springframework.stereotype.Component;

/**
 * Hält die JDA-Instanz, damit Services den Bot nutzen können,
 * ohne zirkuläre Abhängigkeiten zu erzeugen (Bot wird erst nach App-Start gebaut).
 */
@Component
public class JdaHolder {

    private volatile JDA jda;

    public void set(JDA jda) {
        this.jda = jda;
    }

    public JDA get() {
        return jda;
    }

    public boolean isReady() {
        return jda != null;
    }
}
