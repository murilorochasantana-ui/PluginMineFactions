package br.com.murilo.muriloplug.stats;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/** Disparado depois que uma kill (com suas assistências e a morte da vítima) é contada. */
public final class PlayerKillEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID killer;
    private final UUID victim;
    private final List<UUID> assistants;

    public PlayerKillEvent(UUID killer, UUID victim, List<UUID> assistants) {
        this.killer = killer;
        this.victim = victim;
        this.assistants = List.copyOf(assistants);
    }

    public UUID getKiller() {
        return killer;
    }

    public UUID getVictim() {
        return victim;
    }

    /** Jogadores que ganharam assistência (nunca inclui o killer nem a vítima). */
    public List<UUID> getAssistants() {
        return assistants;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
