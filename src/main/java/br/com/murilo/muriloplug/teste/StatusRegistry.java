package br.com.murilo.muriloplug.teste;

import org.bukkit.entity.Entity;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Marcas das habilidades em entidades ("Preso", "Provocado", "Vinculado"...), cada uma com duração. As habilidades só
 * registram aqui; o efeito delas não depende do registro. Vale para jogadores, mobs e bonecos de teste.
 */
public final class StatusRegistry {

    /** Duração para marcas que valem até serem removidas (o texto do boneco não mostra o tempo). */
    public static final double INDEFINITE = 3600;

    private static final int PRUNE_SIZE = 256;

    private final Map<UUID, Map<String, Long>> statuses = new HashMap<>();

    public void add(Entity entity, String name, double seconds) {
        add(entity.getUniqueId(), name, seconds);
    }

    public void add(UUID id, String name, double seconds) {
        if (statuses.size() > PRUNE_SIZE) {
            long now = System.currentTimeMillis();
            statuses.values().removeIf(marks -> {
                marks.values().removeIf(end -> end <= now);
                return marks.isEmpty();
            });
        }
        statuses.computeIfAbsent(id, k -> new LinkedHashMap<>()).put(name, System.currentTimeMillis() + (long) (seconds * 1000));
    }

    public void remove(Entity entity, String name) {
        remove(entity.getUniqueId(), name);
    }

    public void remove(UUID id, String name) {
        Map<String, Long> marks = statuses.get(id);
        if (marks != null) {
            marks.remove(name);
            if (marks.isEmpty()) {
                statuses.remove(id);
            }
        }
    }

    public void clear(Entity entity) {
        statuses.remove(entity.getUniqueId());
    }

    public boolean has(Entity entity, String name) {
        return get(entity).containsKey(name);
    }

    /** Marcas ativas da entidade e os segundos que faltam para cada uma (na ordem em que foram marcadas). */
    public Map<String, Double> get(Entity entity) {
        Map<String, Long> marks = statuses.get(entity.getUniqueId());
        Map<String, Double> active = new LinkedHashMap<>();
        if (marks == null) {
            return active;
        }
        long now = System.currentTimeMillis();
        marks.values().removeIf(end -> end <= now);
        marks.forEach((name, end) -> active.put(name, (end - now) / 1000.0));
        if (marks.isEmpty()) {
            statuses.remove(entity.getUniqueId());
        }
        return active;
    }
}
