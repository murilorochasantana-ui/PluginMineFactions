package br.com.murilo.muriloplug.claim;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Disparado quando um inimigo quebra, coloca ou abre algo num claim que só deixou porque estava vulnerável (Núcleo
 * destruído) ou enfraquecido (em dívida). Usado pelo registro de raids.
 */
public final class ClaimRaidAccessEvent extends Event {

    public enum Action { BREAK, PLACE, OPEN }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Claim claim;
    private final Block block;
    private final Action action;
    private final boolean container;

    public ClaimRaidAccessEvent(Player player, Claim claim, Block block, Action action, boolean container) {
        this.player = player;
        this.claim = claim;
        this.block = block;
        this.action = action;
        this.container = container;
    }

    public Player getPlayer() {
        return player;
    }

    public Claim getClaim() {
        return claim;
    }

    public Block getBlock() {
        return block;
    }

    public Action getAction() {
        return action;
    }

    /** Se o bloco é um baú/container (quebrado ou aberto). */
    public boolean isContainer() {
        return container;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
