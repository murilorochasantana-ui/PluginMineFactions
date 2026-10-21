package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pena: voo por alguns segundos (só em sobrevivência/aventura). Ao acabar o tempo, tira o voo, cancela o dano da
 * próxima queda e só então começa a recarga. Morte, saída ou deixar de ser Suporte acabam o voo na hora.
 */
public final class FlightManager implements Listener {

    private static final long TICK_PERIOD = 5L;

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final SuporteSettings settings;
    private final SuporteCooldowns cooldowns;
    private final Map<UUID, Long> flyingUntil = new HashMap<>();
    private final Map<UUID, Long> fallProtectionUntil = new HashMap<>();

    public FlightManager(JavaPlugin plugin, ClassManager classes, SuporteSettings settings,
                         SuporteCooldowns cooldowns) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.cooldowns = cooldowns;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    public boolean isActive(UUID id) {
        return flyingUntil.containsKey(id);
    }

    /** Começa o voo. Retorna false (sem gastar nada) se o modo de jogo não permite. */
    public boolean begin(Player player) {
        if (!survivalLike(player)) {
            player.sendActionBar(ShopUi.text("§cVocê não pode usar a Pena neste modo de jogo."));
            return false;
        }
        UUID id = player.getUniqueId();
        flyingUntil.put(id, System.currentTimeMillis() + settings.flightSeconds() * 1000L);
        fallProtectionUntil.remove(id);
        player.setAllowFlight(true);
        player.setFlying(true);
        showTime(player);
        return true;
    }

    /** Tira o voo de todos (onDisable). */
    public void endAll() {
        for (UUID id : Set.copyOf(flyingUntil.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                end(player, false);
            }
        }
        flyingUntil.clear();
    }

    private static boolean survivalLike(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    private void showTime(Player player) {
        Long until = flyingUntil.get(player.getUniqueId());
        if (until != null) {
            int seconds = (int) Math.ceil(Math.max(0, until - System.currentTimeMillis()) / 1000.0);
            player.sendActionBar(ShopUi.text("§dVoo: " + seconds + "s"));
        }
    }

    /** Acaba o voo. {@code natural}: o tempo acabou (dá proteção de queda e inicia a recarga). */
    private void end(Player player, boolean natural) {
        UUID id = player.getUniqueId();
        if (flyingUntil.remove(id) == null) {
            return;
        }
        if (survivalLike(player)) { // criativo e espectador não são mexidos
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        if (natural) {
            fallProtectionUntil.put(id, System.currentTimeMillis() + settings.fallProtectionSeconds() * 1000L);
            cooldowns.start(player, SuporteItem.VOO, settings.cooldownSeconds(SuporteItem.VOO));
            player.sendActionBar(ShopUi.text("§dO voo acabou."));
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID id : Set.copyOf(flyingUntil.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                flyingUntil.remove(id);
            } else if (player.isDead() || !classes.hasClass(id, PlayerClass.SUPORTE)) {
                end(player, false);
            } else if (flyingUntil.get(id) <= now) {
                end(player, true);
            } else {
                showTime(player);
            }
        }
    }

    /** Cancela o dano da próxima queda depois do voo (até o prazo da proteção). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Long until = fallProtectionUntil.remove(player.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        end(event.getEntity(), false);
        fallProtectionUntil.remove(event.getEntity().getUniqueId());
    }

    /** Tira o voo antes de o jogador ser salvo. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        end(event.getPlayer(), false);
        fallProtectionUntil.remove(event.getPlayer().getUniqueId());
    }

    /** Se o servidor caiu no meio de um voo, o jogador não fica com voo para sempre. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!flyingUntil.containsKey(player.getUniqueId()) && survivalLike(player) && player.getAllowFlight()) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        Player player = event.getPlayer();
        if (event.getOldClass() == PlayerClass.SUPORTE && player != null) {
            end(player, false);
            fallProtectionUntil.remove(event.getPlayerId());
        }
    }
}
