package br.com.murilo.muriloplug.clan.base;

import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Disparado depois que uma explosão tira pontos do Núcleo ou do Cofre de um clan (usado pelo registro de raids). */
public final class BaseDamageEvent extends Event {

    public enum Kind { CORE, VAULT }

    private static final HandlerList HANDLERS = new HandlerList();

    private final String clanTag;
    private final Kind kind;
    private final Location location;
    private final UUID attacker;
    private final int damage;
    private final boolean destroyed;
    private final long moneyLost;

    public BaseDamageEvent(String clanTag, Kind kind, Location location, UUID attacker, int damage, boolean destroyed,
                           long moneyLost) {
        this.clanTag = clanTag;
        this.kind = kind;
        this.location = location.clone();
        this.attacker = attacker;
        this.damage = damage;
        this.destroyed = destroyed;
        this.moneyLost = moneyLost;
    }

    public String getClanTag() {
        return clanTag;
    }

    public Kind getKind() {
        return kind;
    }

    public Location getLocation() {
        return location.clone();
    }

    /** Quem causou a explosão (null se não deu para saber). */
    public UUID getAttacker() {
        return attacker;
    }

    public int getDamage() {
        return damage;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    /** Dinheiro do Cofre que caiu no chão (só quando o Cofre é destruído). */
    public long getMoneyLost() {
        return moneyLost;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
