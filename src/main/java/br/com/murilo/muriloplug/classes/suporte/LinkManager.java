package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.clan.ClanManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.teste.StatusRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vínculo do Núcleo Pesado: um Suporte com um aliado. Rastro roxo visível para todos. O aliado leva menos dano
 * (resistência, antes da divisão) e passa parte do que sobra para o Suporte (com um tipo de dano que a armadura reduz
 * e sem atacante, então não vira KDA), o que dá Harmonia ao Suporte. Também recebe parte das autocuras do Suporte e
 * pode ser salvo de um golpe mortal pela {@link TrocaEquivalente}.
 */
public final class LinkManager implements Listener {

    private static final Set<DamageCause> EXEMPT = Set.of(
            DamageCause.VOID, DamageCause.KILL, DamageCause.SUICIDE, DamageCause.WORLD_BORDER);
    private static final long TICK_PERIOD = 5L;
    private static final long WARNING_COOLDOWN_MILLIS = 2_000L;
    private static final Particle.DustOptions TRAIL = new Particle.DustOptions(Color.fromRGB(150, 40, 220), 1.2f);

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final ClanManager clans;
    private final AuthManager auth;
    private final SuporteSettings settings;
    private final SuporteTargets targets;
    private final SuporteAssists assists;
    private final StatusRegistry statuses;
    private final HarmoniaManager harmony;
    private final TrocaEquivalente swap;
    /** Suporte -> aliado vinculado. */
    private final Map<UUID, UUID> bySupport = new HashMap<>();
    /** Aliado vinculado -> Suporte. */
    private final Map<UUID, UUID> byAlly = new HashMap<>();
    private final Map<UUID, Long> lastWarning = new HashMap<>();
    /** True enquanto o dano repassado é aplicado no Suporte (evita dividir o dano de novo). */
    private boolean sharing;

    public LinkManager(JavaPlugin plugin, ClassManager classes, ClanManager clans, AuthManager auth,
                       SuporteSettings settings, SuporteTargets targets, SuporteAssists assists,
                       StatusRegistry statuses, HarmoniaManager harmony, TrocaEquivalente swap) {
        this.statuses = statuses;
        this.harmony = harmony;
        this.swap = swap;
        this.plugin = plugin;
        this.classes = classes;
        this.clans = clans;
        this.auth = auth;
        this.settings = settings;
        this.targets = targets;
        this.assists = assists;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    /** Desfaz todos os vínculos sem avisar (onDisable). */
    public void endAll() {
        bySupport.clear();
        byAlly.clear();
    }

    /** Aliado vinculado ao Suporte (vivo e carregado), ou null. */
    public LivingEntity linkedAlly(UUID support) {
        UUID allyId = bySupport.get(support);
        Entity found = allyId == null ? null : Bukkit.getEntity(allyId);
        return found instanceof LivingEntity living && !living.isDead() ? living : null;
    }

    /**
     * O Suporte curou a si mesmo: o aliado vinculado (por perto) recebe {@code vinculo.cura-compartilhada} dessa cura.
     * Retorna quanto o aliado curou de fato.
     */
    public double shareSelfHeal(Player support, double healed) {
        LivingEntity ally = linkedAlly(support.getUniqueId());
        double amount = healed * settings.linkSharedHeal();
        if (ally == null || amount <= 0 || !tooClose(support, ally)) {
            return 0;
        }
        AttributeInstance max = ally.getAttribute(Attribute.MAX_HEALTH);
        double limit = max == null ? 20 : max.getValue();
        double before = ally.getHealth();
        ally.setHealth(Math.min(limit, before + amount));
        double gained = ally.getHealth() - before;
        if (gained > 0) {
            ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 1.8, 0), 3, 0.3, 0.2, 0.3, 0);
        }
        return gained;
    }

    // ------------------------------------------------------------ uso do item

    /** Clique direito com o Núcleo: vincula ao aliado na mira, troca de aliado, ou desfaz. */
    public void use(Player support) {
        UUID id = support.getUniqueId();
        UUID current = bySupport.get(id);
        LivingEntity aimed = targets.aimedTarget(support, settings.linkRange());
        if (aimed == null) {
            if (current != null) {
                end(id, "você desfez o vínculo.");
            } else {
                support.sendActionBar(ShopUi.text("§cMire em um aliado do seu clan para se vincular."));
            }
            return;
        }
        UUID allyId = aimed.getUniqueId();
        if (!targets.isAlly(support, aimed)) {
            support.sendActionBar(ShopUi.text("§cSó é possível se vincular a um aliado do seu clan."));
            return;
        }
        if (allyId.equals(current)) {
            end(id, "você desfez o vínculo.");
            return;
        }
        UUID otherSupport = byAlly.get(allyId);
        if (otherSupport != null && !otherSupport.equals(id)) {
            support.sendActionBar(ShopUi.text("§cEste aliado já está vinculado a outro Suporte."));
            return;
        }
        if (bySupport.containsKey(allyId) || byAlly.containsKey(id)) {
            support.sendActionBar(ShopUi.text("§cUm vínculo não pode ser ligado a outro Suporte."));
            return;
        }
        if (current != null) {
            end(id, "você trocou de aliado.");
        }
        bySupport.put(id, allyId);
        byAlly.put(allyId, id);
        if (aimed instanceof Player) {
            assists.record(id, allyId);
        }
        statuses.add(allyId, "Vinculado", StatusRegistry.INDEFINITE);
        int percent = (int) Math.round(settings.linkPercent() * 100);
        int resistance = (int) Math.round(settings.linkResistance() * 100);
        support.sendMessage("§5Você se vinculou a " + nameOf(aimed) + "§5: ele leva " + resistance
                + "% menos dano e você recebe " + percent + "% do dano dele.");
        tell(aimed, "§5" + support.getName() + " se vinculou a você: você leva " + resistance
                + "% menos dano e ele recebe " + percent + "% do seu dano.");
        support.getWorld().playSound(support.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
    }

    /** Desfaz o vínculo do Suporte, avisando os dois. */
    private void end(UUID supportId, String reason) {
        UUID allyId = bySupport.remove(supportId);
        if (allyId == null) {
            return;
        }
        byAlly.remove(allyId);
        statuses.remove(allyId, "Vinculado");
        Player support = Bukkit.getPlayer(supportId);
        Entity found = Bukkit.getEntity(allyId);
        LivingEntity ally = found instanceof LivingEntity living ? living : null;
        if (support != null && ally != null) {
            support.sendMessage("§5O vínculo com " + nameOf(ally) + " acabou: " + reason);
            tell(ally, "§5O vínculo com " + support.getName() + " acabou: " + reason);
        } else if (support != null) {
            support.sendMessage("§5O vínculo acabou: " + reason);
        } else if (ally != null) {
            tell(ally, "§5O vínculo acabou: " + reason);
        }
    }

    private static String nameOf(LivingEntity entity) {
        return entity instanceof Player player ? player.getName() : "Boneco de Teste";
    }

    /** Mensagem só para jogadores (o boneco de teste não lê chat). */
    private static void tell(LivingEntity entity, String message) {
        if (entity instanceof Player player) {
            player.sendMessage(message);
        }
    }

    /** Desfaz o vínculo em que o jogador está, seja como Suporte ou como aliado. */
    private void endFor(UUID player, String reason) {
        if (bySupport.containsKey(player)) {
            end(player, reason);
        }
        UUID support = byAlly.get(player);
        if (support != null) {
            end(support, reason);
        }
    }

    // ------------------------------------------------------------ relógio

    private void tick() {
        for (UUID supportId : List.copyOf(bySupport.keySet())) {
            UUID allyId = bySupport.get(supportId);
            if (allyId == null) {
                continue;
            }
            Player support = Bukkit.getPlayer(supportId);
            Entity found = Bukkit.getEntity(allyId);
            LivingEntity ally = found instanceof LivingEntity living ? living : null;
            String reason = null;
            if (support == null || ally == null) {
                reason = "um dos dois saiu do servidor.";
            } else if (support.isDead() || ally.isDead()) {
                reason = "um dos dois morreu.";
            } else if (!classes.hasClass(supportId, PlayerClass.SUPORTE)) {
                reason = "você deixou de ser Suporte.";
            } else if (!targets.isAlly(support, ally)) {
                reason = "vocês não estão mais no mesmo clan.";
            } else if (!tooClose(support, ally)) {
                reason = "vocês ficaram a mais de " + (int) settings.linkMaxDistance() + " blocos de distância.";
            }
            if (reason != null) {
                end(supportId, reason);
                continue;
            }
            drawTrail(support, ally);
            if (ally instanceof Player) {
                assists.record(supportId, allyId); // o vínculo ativo conta como ajuda contínua
            }
        }
    }

    private boolean tooClose(Player support, LivingEntity ally) {
        return support.getWorld().equals(ally.getWorld())
                && support.getLocation().distance(ally.getLocation()) <= settings.linkMaxDistance();
    }

    private void drawTrail(Player support, LivingEntity ally) {
        Location from = support.getLocation().add(0, 1, 0);
        Location to = ally.getLocation().add(0, 1, 0);
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        if (length < 0.01) {
            return;
        }
        step.normalize().multiply(0.5);
        Location point = from.clone();
        for (double travelled = 0; travelled < length; travelled += 0.5) {
            // force = true: aparece para todos os jogadores, mesmo longe.
            support.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, TRAIL, true);
            point.add(step);
        }
    }

    // ------------------------------------------------------------ dano compartilhado

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (sharing || !(event.getEntity() instanceof LivingEntity ally) || EXEMPT.contains(event.getCause())) {
            return;
        }
        UUID supportId = byAlly.get(ally.getUniqueId());
        Player support = supportId == null ? null : Bukkit.getPlayer(supportId);
        if (support == null || support.isDead() || !tooClose(support, ally)) {
            return;
        }
        // Resistência do vinculado, antes da divisão.
        double resistance = settings.linkResistance();
        if (resistance > 0) {
            event.setDamage(event.getDamage() * (1 - resistance));
        }
        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0) {
            return;
        }
        double share = 0;
        if (support.getHealth() <= settings.linkStopHealth()) {
            warnLowHealth(support); // com a vida baixa, o Suporte não divide: o aliado recebe tudo
        } else {
            // Nunca deixa o Suporte morrer só pelo vínculo (a armadura ainda reduz um pouco mais).
            share = Math.max(0, Math.min(finalDamage * settings.linkPercent(), support.getHealth() - 1));
        }
        if (share > 0) {
            event.setDamage(event.getDamage() * (1 - share / finalDamage));
        }
        // O que sobrou ainda mataria: a Troca Equivalente pode salvar (e aí o dano não chega a ser dividido).
        if (event.getFinalDamage() >= ally.getHealth() && swap.tryRescue(support, ally)) {
            event.setCancelled(true);
            return;
        }
        if (share > 0) {
            pass(support, share);
            harmony.addForLink(supportId, share);
        }
    }

    /** Aplica a parte no Suporte sem atacante (não entra no KDA) e sem lhe dar imunidade. */
    private void pass(Player support, double share) {
        int previousTicks = support.getNoDamageTicks();
        double previousLast = support.getLastDamage();
        sharing = true;
        try {
            support.setNoDamageTicks(0);
            support.damage(share, DamageSource.builder(DamageType.GENERIC).build());
        } finally {
            sharing = false;
            support.setNoDamageTicks(previousTicks);
            support.setLastDamage(previousLast);
        }
    }

    private void warnLowHealth(Player support) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(support.getUniqueId());
        if (last == null || now - last >= WARNING_COOLDOWN_MILLIS) {
            lastWarning.put(support.getUniqueId(), now);
            support.sendActionBar(ShopUi.text("§cVida baixa: o vínculo parou de dividir o dano."));
        }
    }

    // ------------------------------------------------------------ fim do vínculo

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        endFor(event.getEntity().getUniqueId(), "um dos dois morreu.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        endFor(event.getPlayer().getUniqueId(), "um dos dois saiu do servidor.");
        lastWarning.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getOldClass() == PlayerClass.SUPORTE) {
            end(event.getPlayerId(), "você deixou de ser Suporte.");
        }
    }
}
