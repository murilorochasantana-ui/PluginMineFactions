package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.stats.DamageTracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quem o Suporte ajudou (curou, fortaleceu ou manteve vinculado) e quando. Se o aliado pegar uma kill dentro de
 * {@link DamageTracker#ASSIST_SECONDS} segundos, o Suporte ganha uma assistência (o StatsManager consulta aqui).
 */
public final class SuporteAssists {

    /** Por aliado: Suporte -> momento da última ajuda. */
    private final Map<UUID, LinkedHashMap<UUID, Long>> helped = new HashMap<>();

    public void record(UUID support, UUID ally) {
        LinkedHashMap<UUID, Long> supports = helped.computeIfAbsent(ally, k -> new LinkedHashMap<>());
        supports.remove(support);
        supports.put(support, System.currentTimeMillis());
        prune(supports);
    }

    /** Suportes que ajudaram o aliado nos últimos 30s. */
    public List<UUID> supportersOf(UUID ally) {
        LinkedHashMap<UUID, Long> supports = helped.get(ally);
        if (supports == null) {
            return List.of();
        }
        prune(supports);
        return new ArrayList<>(supports.keySet());
    }

    private static void prune(Map<UUID, Long> supports) {
        long now = System.currentTimeMillis();
        supports.values().removeIf(time -> now - time > DamageTracker.ASSIST_SECONDS * 1000L);
    }
}
