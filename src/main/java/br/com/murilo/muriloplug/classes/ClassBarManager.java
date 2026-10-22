package br.com.murilo.muriloplug.classes;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Barra de XP como barra de recurso das classes. Cada classe registra um {@link Provider} que diz o nível (número
 * verde) e o progresso (0 a 1) para o jogador; este gerenciador é o único que mostra a barra, com
 * {@link Player#sendExperienceChange(float, int)}: muda só o que o jogador vê e NÃO dá XP (o XP real fica sempre 0).
 * O jogo reenvia o XP real ao entrar, renascer, trocar de mundo e de modo de jogo, então a barra é refeita depois
 * desses momentos, a cada segundo e quando a classe pede ({@link #update}). Sem classe, ou sem provedor: barra zerada.
 */
public final class ClassBarManager implements Listener {

    /** O que mostrar: nível (número verde) e progresso da barra (0.0 a 1.0). */
    public record Bar(int level, float progress) {
        public static final Bar EMPTY = new Bar(0, 0f);
    }

    @FunctionalInterface
    public interface Provider {
        Bar bar(Player player);
    }

    private static final long REFRESH_TICKS = 20L;

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final Map<PlayerClass, Provider> providers = new EnumMap<>(PlayerClass.class);

    public ClassBarManager(JavaPlugin plugin, ClassManager classes) {
        this.plugin = plugin;
        this.classes = classes;
    }

    public void register(PlayerClass playerClass, Provider provider) {
        providers.put(playerClass, provider);
    }

    /** Liga a atualização de 1 em 1 segundo e já desenha a barra de quem está online. */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateAll, REFRESH_TICKS, REFRESH_TICKS);
        updateAll();
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    public void update(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            update(player);
        }
    }

    public void update(Player player) {
        PlayerClass playerClass = classes.getClass(player.getUniqueId());
        Provider provider = playerClass == null ? null : providers.get(playerClass);
        Bar bar = provider == null ? Bar.EMPTY : provider.bar(player);
        float progress = Math.max(0f, Math.min(1f, bar.progress()));
        player.sendExperienceChange(progress, Math.max(0, bar.level()));
    }

    /** Depois do evento: o jogo reenvia o XP real no meio dele, por cima da nossa barra. */
    private void updateLater(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                update(player);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updateLater(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        updateLater(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        updateLater(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        updateLater(event.getPlayer());
    }

    @EventHandler
    public void onGameMode(PlayerGameModeChangeEvent event) {
        updateLater(event.getPlayer());
    }

    /** Passa a valer o provedor da classe nova (ou a barra zerada, se não houver). */
    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            update(player);
        }
    }
}
