package br.com.murilo.muriloplug.classes.mago;

import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lançamento das staffs: confere classe, login, recarga e mana, executa a magia e só então cobra mana e recarga
 * (uma magia que não achou alvo devolve false e não custa nada). Também controla a carga do teleporte em grupo.
 */
public final class StaffService {

    private static final long WARNING_COOLDOWN_MILLIS = 2_000L;
    /** A carga confere o jogador a cada 5 ticks. */
    private static final int CHARGE_PERIOD = 5;

    private final JavaPlugin plugin;
    private final ManaManager mana;
    private final AuthManager auth;
    private final StaffItems items;
    private final MagoSettings settings;
    private final BoltSpells bolts;
    private final LightningSpell lightning;
    private final WindSpell wind;
    private final TeleportSpell teleport;
    /** Fim da recarga (ms) de cada staff de cada jogador. */
    private final Map<UUID, Map<StaffType, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, BukkitRunnable> charges = new HashMap<>();
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    public StaffService(JavaPlugin plugin, ManaManager mana, AuthManager auth, StaffItems items,
                        MagoSettings settings, SpellTargets targets) {
        this.plugin = plugin;
        this.mana = mana;
        this.auth = auth;
        this.items = items;
        this.settings = settings;
        this.bolts = new BoltSpells(plugin, targets, settings);
        this.lightning = new LightningSpell(targets, settings);
        this.wind = new WindSpell(targets, settings);
        this.teleport = new TeleportSpell(settings, other -> auth.isLoggedIn(other.getUniqueId()));
    }

    public boolean isMage(Player player) {
        return mana.isMage(player.getUniqueId());
    }

    public boolean hasFallProtection(UUID player) {
        return teleport.hasFallProtection(player);
    }

    public boolean isCharging(UUID player) {
        return charges.containsKey(player);
    }

    /** Avisa que só Magos usam a staff, no máximo uma vez a cada 2s. */
    public void warnNotMage(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last == null || now - last >= WARNING_COOLDOWN_MILLIS) {
            lastWarning.put(player.getUniqueId(), now);
            player.sendMessage("§cSomente Magos podem usar esta staff.");
        }
    }

    // ------------------------------------------------------------ lançamento

    /** Lança a magia (teleporte normal, só o Mago). */
    public void cast(Player player, StaffType type) {
        cast(player, type, false);
    }

    private void cast(Player player, StaffType type, boolean group) {
        if (!canCast(player, type)) {
            return;
        }
        boolean success = switch (type) {
            case FOGO -> bolts.fire(player);
            case GELO -> bolts.ice(player);
            case RAIO -> lightning.cast(player);
            case TELEPORTE -> teleport.cast(player, group);
            case VENTO -> wind.cast(player);
        };
        if (success) {
            commit(player, type);
        } else if (type == StaffType.RAIO) {
            actionBar(player, "§cNenhum alvo na mira.");
        } else if (type == StaffType.TELEPORTE) {
            actionBar(player, "§cNão há um lugar seguro nessa direção.");
        }
    }

    /** Confere login, classe, recarga e mana, avisando na action bar. */
    private boolean canCast(Player player, StaffType type) {
        if (!auth.isLoggedIn(player.getUniqueId()) || !isMage(player)) {
            return false;
        }
        long remaining = cooldownRemainingMillis(player, type);
        if (remaining > 0) {
            actionBar(player, "§cRecarga: " + (int) Math.ceil(remaining / 1000.0) + "s");
            return false;
        }
        int cost = settings.manaCost(type);
        if (!mana.has(player.getUniqueId(), cost)) {
            actionBar(player, "§cMana insuficiente (" + mana.get(player.getUniqueId()) + "/" + cost + ")");
            return false;
        }
        return true;
    }

    /** Cobra a mana, inicia a recarga (e a animação no item) e mostra o gasto. */
    private void commit(Player player, StaffType type) {
        UUID id = player.getUniqueId();
        int cost = settings.manaCost(type);
        mana.spend(id, cost);
        double seconds = settings.cooldownSeconds(type);
        cooldowns.computeIfAbsent(id, k -> new EnumMap<>(StaffType.class))
                .put(type, System.currentTimeMillis() + (long) (seconds * 1000));
        ItemStack held = player.getInventory().getItemInMainHand();
        if (items.read(held) == type) {
            player.setCooldown(held, (int) Math.ceil(seconds * 20));
        }
        actionBar(player, "§b-" + cost + " mana (" + mana.get(id) + "/" + mana.max(id) + ")");
    }

    private long cooldownRemainingMillis(Player player, StaffType type) {
        Map<StaffType, Long> own = cooldowns.get(player.getUniqueId());
        Long until = own == null ? null : own.get(type);
        return until == null ? 0 : until - System.currentTimeMillis();
    }

    private void actionBar(Player player, String legacy) {
        player.sendActionBar(ShopUi.text(legacy));
    }

    // ------------------------------------------------------------ carga do teleporte em grupo

    /**
     * SHIFT + clique direito com a Staff de Teleporte: segurando o SHIFT pelo tempo da carga, leva os jogadores
     * próximos junto. Soltar o SHIFT antes faz o teleporte normal, só do Mago.
     */
    public void startCharge(Player player) {
        UUID id = player.getUniqueId();
        if (charges.containsKey(id) || !canCast(player, StaffType.TELEPORTE)) {
            return;
        }
        int totalTicks = (int) (settings.staff(StaffType.TELEPORTE, "carga-segundos") * 20);
        int seconds = Math.max(1, totalTicks / 20);

        BukkitRunnable task = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                elapsed += CHARGE_PERIOD;
                if (!player.isOnline() || player.isDead() || !auth.isLoggedIn(id) || !holdsTeleportStaff(player)) {
                    stop(id);
                    if (player.isOnline()) {
                        actionBar(player, "§cCarga do teleporte cancelada.");
                    }
                    return;
                }
                if (!player.isSneaking()) {
                    stop(id);
                    cast(player, StaffType.TELEPORTE, false);
                    return;
                }
                if (elapsed >= totalTicks) {
                    stop(id);
                    cast(player, StaffType.TELEPORTE, true);
                    return;
                }
                int step = Math.min(seconds, elapsed / 20 + 1);
                actionBar(player, "§5Carregando teleporte " + step + "/" + seconds + "...");
                player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 25, 0.8, 0.9, 0.8, 0.4);
            }
        };
        charges.put(id, task);
        task.runTaskTimer(plugin, CHARGE_PERIOD, CHARGE_PERIOD);
    }

    private boolean holdsTeleportStaff(Player player) {
        return items.read(player.getInventory().getItemInMainHand()) == StaffType.TELEPORTE;
    }

    /** Cancela a carga sem gastar nada (dano recebido, morte, saída). */
    public void cancelCharge(Player player, String message) {
        if (stop(player.getUniqueId()) && message != null && player.isOnline()) {
            actionBar(player, message);
        }
    }

    private boolean stop(UUID id) {
        BukkitRunnable task = charges.remove(id);
        if (task == null) {
            return false;
        }
        task.cancel();
        return true;
    }

    /** Esquece o estado de um jogador que saiu. */
    public void forget(UUID id) {
        stop(id);
        cooldowns.remove(id);
        lastWarning.remove(id);
        teleport.forget(id);
    }
}
