package br.com.murilo.muriloplug.classes.mago;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Entidades temporárias das magias (espíritos da Invocação e cópias da Ilusão). Cada uma leva no
 * PersistentDataContainer o UUID do Mago dono; as que sobrarem de uma queda do servidor são removidas ao ligar
 * ({@link #sweep()}) e quando o chunk delas carrega.
 */
final class TempEntities implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey key;

    TempEntities(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "temp_entity");
    }

    /** Marca a entidade como temporária do Mago e a impede de ser salva com o chunk. */
    void tag(Entity entity, UUID owner) {
        entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, owner.toString());
        entity.setPersistent(false);
    }

    boolean isTagged(Entity entity) {
        return entity.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    /** UUID do Mago dono, ou null se a entidade não é temporária. */
    UUID ownerOf(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Remove todas as entidades temporárias dos mundos carregados. */
    void sweep() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (isTagged(entity)) {
                    entity.remove();
                }
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isTagged(entity)) {
                // No tick seguinte, fora do evento de carga.
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (entity.isValid()) {
                        entity.remove();
                    }
                });
            }
        }
    }
}
