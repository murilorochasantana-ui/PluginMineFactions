package br.com.murilo.muriloplug.stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Guarda, para cada vítima, quais jogadores causaram dano nela nos últimos {@link #ASSIST_SECONDS} segundos. */
public final class DamageTracker {

    public static final int ASSIST_SECONDS = 30;

    /** Por vítima: atacante -> momento do último golpe. A ordem é do golpe mais antigo para o mais recente. */
    private final Map<UUID, LinkedHashMap<UUID, Long>> hits = new HashMap<>();

    public void record(UUID victim, UUID attacker) {
        long now = System.currentTimeMillis();
        LinkedHashMap<UUID, Long> attackers = hits.computeIfAbsent(victim, k -> new LinkedHashMap<>());
        attackers.remove(attacker);
        attackers.put(attacker, now);
        prune(attackers, now);
    }

    /** Atacantes recentes da vítima, do mais antigo para o mais recente (o último é quem bateu por último). */
    public List<UUID> recentAttackers(UUID victim) {
        LinkedHashMap<UUID, Long> attackers = hits.get(victim);
        if (attackers == null) {
            return List.of();
        }
        prune(attackers, System.currentTimeMillis());
        return new ArrayList<>(attackers.keySet());
    }

    public void clear(UUID victim) {
        hits.remove(victim);
    }

    /** Remove os registros vencidos de todos os jogadores. */
    public void purge() {
        long now = System.currentTimeMillis();
        Iterator<LinkedHashMap<UUID, Long>> iterator = hits.values().iterator();
        while (iterator.hasNext()) {
            LinkedHashMap<UUID, Long> attackers = iterator.next();
            prune(attackers, now);
            if (attackers.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private static void prune(Map<UUID, Long> attackers, long now) {
        attackers.values().removeIf(time -> now - time > ASSIST_SECONDS * 1000L);
    }
}
