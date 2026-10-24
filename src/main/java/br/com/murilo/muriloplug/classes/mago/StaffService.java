package br.com.murilo.muriloplug.classes.mago;

import br.com.murilo.muriloplug.teste.TestMode;
import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.teste.StatusRegistry;
import org.bukkit.Particle;
import org.bukkit.event.Listener;
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
    private final ControlSpells control;
    private final SupportSpells support;
    private final SummonSpell summon;
    private final IllusionSpell illusion;
    private final TempEntities temp;
    private final LightningSpell lightning;
    private final WindSpell wind;
    private final TeleportSpell teleport;
    private final ArcaneMissile missiles;
    private final ArcanePower arcane;
    private final MagoCombos combos;
    private final GrimoireItems grimoireItems;
    private final GrimoireManager grimoire;
    /** Momento (ms) da última troca de magia do Grimório de cada jogador (um clique pode gerar dois eventos). */
    private final Map<UUID, Long> lastCycle = new HashMap<>();
    /** Momento (ms) do último Míssil Arcano de cada jogador. */
    private final Map<UUID, Long> lastMissile = new HashMap<>();
    /** Fim da recarga (ms) de cada staff de cada jogador. */
    private final Map<UUID, Map<StaffType, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, BukkitRunnable> charges = new HashMap<>();
    private TestMode test = TestMode.inactive();
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    public StaffService(JavaPlugin plugin, ManaManager mana, AuthManager auth, StaffItems items,
                        MagoSettings settings, SpellTargets targets, ArcanePower arcane,
                        GrimoireItems grimoireItems, GrimoireManager grimoire, StatusRegistry statuses) {
        this.plugin = plugin;
        this.grimoireItems = grimoireItems;
        this.grimoire = grimoire;
        this.arcane = arcane;
        this.missiles = new ArcaneMissile(plugin, targets, mana, settings);
        this.mana = mana;
        this.auth = auth;
        this.items = items;
        this.settings = settings;
        this.combos = new MagoCombos(plugin, settings, statuses);
        this.bolts = new BoltSpells(plugin, targets, settings, combos);
        this.control = new ControlSpells(plugin, targets, settings, bolts);
        this.temp = new TempEntities(plugin);
        this.summon = new SummonSpell(plugin, targets, settings, temp, mana::isMage);
        this.illusion = new IllusionSpell(plugin, targets, settings, temp);
        this.support = new SupportSpells(plugin, targets, settings, illusion);
        // Espíritos e cópias têm os próprios eventos; sobras de uma queda do servidor saem já ao ligar.
        for (Listener listener : new Listener[]{temp, summon, illusion}) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
        temp.sweep();
        this.lightning = new LightningSpell(targets, settings, combos);
        this.wind = new WindSpell(targets, settings, combos);
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

    // ------------------------------------------------------------ staff ou Grimório

    /** True se o item é uma staff ou o Grimório (os itens que lançam magia). */
    public boolean isCaster(ItemStack item) {
        return items.read(item) != null || grimoireItems.isGrimoire(item);
    }

    public boolean isGrimoire(ItemStack item) {
        return grimoireItems.isGrimoire(item);
    }

    /** Magia que o item lança: a da staff, ou a selecionada do Grimório do jogador (null se não há nenhuma). */
    public StaffType spellOf(Player player, ItemStack item) {
        StaffType staff = items.read(item);
        if (staff != null) {
            return staff;
        }
        return grimoireItems.isGrimoire(item) ? grimoire.selected(player.getUniqueId()) : null;
    }

    public void warnNoSpell(Player player) {
        actionBar(player, "§cNenhuma magia equipada. Use /grimorio.");
    }

    /** Shift + clique esquerdo com o Grimório: passa para a próxima magia equipada e mostra a recarga dela. */
    public void cycleSpell(Player player) {
        UUID id = player.getUniqueId();
        if (!auth.isLoggedIn(id) || !isMage(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastCycle.get(id);
        if (last != null && now - last < 300) {
            return;
        }
        lastCycle.put(id, now);
        StaffType spell = grimoire.cycle(id);
        if (spell == null) {
            warnNoSpell(player);
            return;
        }
        grimoire.refresh(player);
        long remaining = cooldownRemainingMillis(player, spell);
        String state = remaining > 0 ? (int) Math.ceil(remaining / 1000.0) + "s" : "pronta";
        actionBar(player, "§5Magia: " + spell.coloredSpellName() + " §7(" + state + ")");
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.6f);
    }

    /** Avisa que só Magos usam a staff, no máximo uma vez a cada 2s. */
    public void warnNotMage(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last == null || now - last >= WARNING_COOLDOWN_MILLIS) {
            lastWarning.put(player.getUniqueId(), now);
            player.sendMessage("§cSomente Magos podem usar este item.");
        }
    }

    // ------------------------------------------------------------ lançamento

    /** Míssil Arcano (clique esquerdo): sem mana nem recarga, só o intervalo mínimo entre um tiro e outro. */
    public void missile(Player player) {
        UUID id = player.getUniqueId();
        if (!auth.isLoggedIn(id) || !isMage(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastMissile.get(id);
        if (last != null && now - last < settings.missileIntervalSeconds() * 1000) {
            return;
        }
        lastMissile.put(id, now);
        illusion.breakIllusion(player);
        missiles.fire(player);
    }

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
            case VENENO -> control.poison(player);
            case METEORO -> control.meteor(player);
            case SANGUE -> control.blood(player);
            case GRAVIDADE -> control.gravity(player);
            case TEMPO -> control.time(player);
            case ESCUDO -> support.shield(player);
            case LUZ -> support.light(player);
            case INVOCACAO -> summon.cast(player);
            case ILUSAO -> illusion.cast(player);
        };
        if (success) {
            commit(player, type);
        } else {
            switch (type) {
                case RAIO, SANGUE -> actionBar(player, "§cNenhum alvo na mira.");
                case TELEPORTE -> actionBar(player, "§cNão há um lugar seguro nessa direção.");
                case METEORO, TEMPO -> actionBar(player, "§cMire em um bloco.");
                case GRAVIDADE -> actionBar(player, "§cNenhum inimigo ao alcance.");
                case INVOCACAO -> actionBar(player, "§cSeus espíritos ainda estão ativos.");
                default -> { }
            }
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
        // Passo Arcano: a magia logo depois do teleporte não gasta mana.
        int cost = combos.hasFreeCast(player.getUniqueId()) ? 0 : settings.manaCost(type);
        if (!mana.has(player.getUniqueId(), cost)) {
            actionBar(player, "§cMana insuficiente (" + mana.get(player.getUniqueId()) + "/" + cost + ")");
            return false;
        }
        return true;
    }

    /** Cobra a mana, inicia a recarga (e a animação no item) e mostra o gasto. */
    private void commit(Player player, StaffType type) {
        UUID id = player.getUniqueId();
        if (type != StaffType.ILUSAO) {
            illusion.breakIllusion(player); // lançar magia desfaz a Ilusão
        }
        boolean free = combos.consumeFreeCast(id);
        int cost = free ? 0 : settings.manaCost(type);
        mana.spend(id, cost);
        arcane.gain(id); // depois do dano da magia: ela usa os acúmulos que já existiam
        boolean testing = test.isActive(id);
        double seconds = settings.cooldownSeconds(type);
        if (testing) {
            // Modo Teste: sem recarga; tira também a animação de 1s que o item põe sozinho ao ser usado.
            plugin.getServer().getScheduler().runTask(plugin, () -> player.setCooldown(items.create(type), 0));
        } else {
            cooldowns.computeIfAbsent(id, k -> new EnumMap<>(StaffType.class))
                    .put(type, System.currentTimeMillis() + (long) (seconds * 1000));
            // Um modelo da staff leva o grupo de recarga dela: a animação vale para todas as staffs da magia, seja a
            // magia lançada pela staff ou pelo Grimório.
            player.setCooldown(items.create(type), (int) Math.ceil(seconds * 20));
        }
        String spent = testing ? "§dModo Teste: sem custo" : free ? "§bMagia grátis!" : "§b-" + cost + " mana";
        // Depois de consumir a mana grátis: o teleporte abre a janela da próxima.
        String next = type == StaffType.TELEPORTE && combos.armFreeCast(id) ? " §8| §bPróxima magia grátis!" : "";
        actionBar(player, spent + " (" + mana.get(id) + "/" + mana.max(id) + ")" + next + arcane.label(id));
    }

    /** Modo Teste dos administradores: sem mana nem recarga. */
    /** Tira as marcas de combo (Gelo e Vento) deste alvo (Purificar do Suporte). */
    public void clearComboMarks(UUID target) {
        combos.clearMarks(target);
    }

    public void setTestMode(TestMode test) {
        this.test = test;
    }

    /** Ao ligar o Modo Teste: zera as recargas em andamento e a animação nas staffs. */
    public void clearCooldowns(Player player) {
        cooldowns.remove(player.getUniqueId());
        for (StaffType type : StaffType.values()) {
            player.setCooldown(items.create(type), 0);
        }
    }

    private long cooldownRemainingMillis(Player player, StaffType type) {
        if (test.isActive(player)) {
            return 0;
        }
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

    /** Segurando a Staff de Teleporte ou o Grimório com o Teleporte selecionado. */
    private boolean holdsTeleportStaff(Player player) {
        return spellOf(player, player.getInventory().getItemInMainHand()) == StaffType.TELEPORTE;
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

    /** Desligamento do plugin: espíritos e cópias somem e quem estava invisível volta a ser visto. */
    public void shutdown() {
        summon.removeEverything();
        illusion.endAll();
        temp.sweep();
    }

    /** Esquece o estado de um jogador que saiu. */
    public void forget(UUID id) {
        stop(id);
        cooldowns.remove(id);
        lastWarning.remove(id);
        lastMissile.remove(id);
        lastCycle.remove(id);
        combos.forget(id);
        teleport.forget(id);
    }
}
