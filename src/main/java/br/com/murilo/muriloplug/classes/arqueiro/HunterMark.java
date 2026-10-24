package br.com.murilo.muriloplug.classes.arqueiro;

import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.teste.StatusRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Marca do Caçador: cada Arqueiro tem no máximo um alvo marcado. O alvo brilha (Glowing, visível para todos: a API
 * do Paper não mostra brilho só para alguns jogadores) e leva mais dano das flechas DESTE Arqueiro.
 */
public final class HunterMark implements Listener {

    public static final String STATUS = "Marcado";

    private record Mark(UUID target, long until) {
    }

    private final StatusRegistry statuses;
    /** Por Arqueiro: o alvo marcado e quando a marca acaba. */
    private final Map<UUID, Mark> marks = new HashMap<>();

    public HunterMark(StatusRegistry statuses) {
        this.statuses = statuses;
    }

    /** Marca o alvo (e desfaz a marca anterior do mesmo Arqueiro). */
    public void mark(Player archer, LivingEntity target, int seconds) {
        clear(archer.getUniqueId());
        marks.put(archer.getUniqueId(), new Mark(target.getUniqueId(), System.currentTimeMillis() + seconds * 1000L));
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, seconds * 20, 0, false, false));
        statuses.add(target, STATUS, seconds);
    }

    public boolean isMarkedBy(UUID archer, Entity target) {
        Mark mark = marks.get(archer);
        if (mark == null) {
            return false;
        }
        if (mark.until() <= System.currentTimeMillis()) {
            marks.remove(archer);
            return false;
        }
        return mark.target().equals(target.getUniqueId());
    }

    /** Tira a marca do Arqueiro (e o brilho e o status do alvo). */
    public void clear(UUID archer) {
        Mark mark = marks.remove(archer);
        if (mark != null) {
            release(mark);
        }
    }

    private void release(Mark mark) {
        Entity entity = Bukkit.getEntity(mark.target());
        if (entity instanceof LivingEntity living) {
            living.removePotionEffect(PotionEffectType.GLOWING);
            statuses.remove(living, STATUS);
        }
    }

    /** Tira todas as marcas que estão neste alvo (Purificar do Suporte). */
    public void clearTarget(UUID target) {
        for (Map.Entry<UUID, Mark> entry : Map.copyOf(marks).entrySet()) {
            if (entry.getValue().target().equals(target)) {
                clear(entry.getKey());
            }
        }
    }

    public void clearAll() {
        for (UUID archer : Set.copyOf(marks.keySet())) {
            clear(archer);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        UUID dead = event.getEntity().getUniqueId();
        marks.values().removeIf(mark -> mark.target().equals(dead));
        statuses.remove(dead, STATUS);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clear(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getOldClass() == PlayerClass.ARQUEIRO) {
            clear(event.getPlayerId());
        }
    }
}
