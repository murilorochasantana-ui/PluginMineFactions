package br.com.murilo.muriloplug.classes;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Disparado depois que a classe de um jogador muda. {@code oldClass} e {@code newClass} podem ser null (sem classe). */
public final class PlayerClassChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final PlayerClass oldClass;
    private final PlayerClass newClass;

    public PlayerClassChangeEvent(UUID playerId, @Nullable PlayerClass oldClass, @Nullable PlayerClass newClass) {
        this.playerId = playerId;
        this.oldClass = oldClass;
        this.newClass = newClass;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    /** O jogador, ou null se estiver offline. */
    public @Nullable Player getPlayer() {
        return Bukkit.getPlayer(playerId);
    }

    public @Nullable PlayerClass getOldClass() {
        return oldClass;
    }

    public @Nullable PlayerClass getNewClass() {
        return newClass;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
