package br.com.murilo.muriloplug.claim;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pré-visualização do claim: desenha a borda com blocos falsos (só o jogador vê)
 * e guarda a área para que o {@code /claim ok} crie exatamente o que foi mostrado.
 */
public final class ClaimPreviewManager implements Listener {

    private static final long EXPIRE_TICKS = 60 * 20L;
    private static final BlockData MARKER = Material.YELLOW_CONCRETE.createBlockData();

    private record Preview(Claim area, List<Location> blocks, BukkitTask expiry) {}

    private final JavaPlugin plugin;
    private final Map<UUID, Preview> previews = new HashMap<>();

    public ClaimPreviewManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Área da pré-visualização atual do jogador, ou null se não houver. */
    public Claim get(UUID player) {
        Preview preview = previews.get(player);
        return preview == null ? null : preview.area();
    }

    /** Mostra a borda de {@code area}, substituindo a pré-visualização anterior do jogador. */
    public void show(Player player, Claim area) {
        cancel(player);

        World world = Bukkit.getWorld(area.world());
        if (world == null) {
            return;
        }

        List<Location> blocks = new ArrayList<>();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                boolean border = x == area.minX() || x == area.maxX() || z == area.minZ() || z == area.maxZ();
                if (!border || !world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                Location location = new Location(world, x, world.getHighestBlockYAt(x, z), z);
                player.sendBlockChange(location, MARKER);
                blocks.add(location);
            }
        }

        UUID id = player.getUniqueId();
        BukkitTask expiry = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Preview expired = previews.remove(id);
            Player online = Bukkit.getPlayer(id);
            if (expired != null && online != null) {
                restore(online, expired);
                online.sendMessage("§eA pré-visualização do claim expirou.");
            }
        }, EXPIRE_TICKS);

        previews.put(id, new Preview(area, blocks, expiry));
    }

    /** Remove a pré-visualização do jogador (e os blocos falsos). Retorna false se não havia nenhuma. */
    public boolean cancel(Player player) {
        Preview preview = previews.remove(player.getUniqueId());
        if (preview == null) {
            return false;
        }
        preview.expiry().cancel();
        restore(player, preview);
        return true;
    }

    public void clearAll() {
        for (Map.Entry<UUID, Preview> entry : previews.entrySet()) {
            entry.getValue().expiry().cancel();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                restore(player, entry.getValue());
            }
        }
        previews.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Preview preview = previews.remove(event.getPlayer().getUniqueId());
        if (preview != null) {
            preview.expiry().cancel();
        }
    }

    private void restore(Player player, Preview preview) {
        // Em outro mundo, as mesmas coordenadas seriam de outro lugar: não há o que restaurar.
        if (!player.getWorld().getName().equals(preview.area().world())) {
            return;
        }
        for (Location location : preview.blocks()) {
            if (location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                player.sendBlockChange(location, location.getBlock().getBlockData());
            }
        }
    }
}
