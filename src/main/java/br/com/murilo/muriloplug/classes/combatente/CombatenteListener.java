package br.com.murilo.muriloplug.classes.combatente;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import org.bukkit.EntityEffect;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Defesa e fraquezas do Combatente: 20% menos dano (queda com redução própria; magia e fogo sem ela, mas com a
 * armadura calculada na mão, já que o jogo não a aplica a esses danos), +30% de dano das magias do Mago, a passiva
 * Último Suspiro e o bloqueio de arco, besta e tridente. No Berserker não há as reduções de 20% e de queda.
 * <p>
 * Fórmula de armadura do Minecraft: {@code dano × (1 − min(20, max(armadura/5, armadura − dano/(2 + dureza/4))) / 25)},
 * com os pontos de armadura e a dureza (resistência) da armadura do Combatente.
 */
public final class CombatenteListener implements Listener {

    private static final Set<DamageCause> EXEMPT = Set.of(
            DamageCause.VOID, DamageCause.KILL, DamageCause.SUICIDE, DamageCause.WORLD_BORDER);
    private static final Set<DamageCause> FIRE = Set.of(DamageCause.FIRE, DamageCause.FIRE_TICK, DamageCause.LAVA);
    private static final long WARNING_COOLDOWN_MILLIS = 2_000L;

    private final ClassManager classes;
    private final CombatenteSettings settings;
    private final FuryManager fury;
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    public CombatenteListener(ClassManager classes, CombatenteSettings settings, FuryManager fury) {
        this.classes = classes;
        this.settings = settings;
        this.fury = fury;
    }

    private boolean isCombatente(Entity entity) {
        return entity instanceof Player player && classes.hasClass(player.getUniqueId(), PlayerClass.COMBATENTE);
    }

    // ------------------------------------------------------------ redução de dano

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || EXEMPT.contains(event.getCause()) || !isCombatente(player)) {
            return;
        }
        boolean berserk = fury.isBerserk(player.getUniqueId());
        DamageCause cause = event.getCause();
        double damage = event.getDamage();
        if (cause == DamageCause.MAGIC) {
            // Fraqueza: só as magias de clique direito do Mago (o Míssil Arcano usa outro tipo de dano).
            if (isMageSpell(event)) {
                damage *= 1 + settings.magicExtraDamage();
            }
            damage *= 1 - armorReduction(player, damage);
        } else if (FIRE.contains(cause)) {
            damage *= 1 - armorReduction(player, damage);
        } else if (cause == DamageCause.FALL) {
            if (!berserk) {
                damage *= 1 - settings.fallReduction();
            }
        } else if (!berserk) {
            damage *= 1 - settings.damageReduction();
        }
        event.setDamage(damage);
    }

    private boolean isMageSpell(EntityDamageEvent event) {
        return DamageType.INDIRECT_MAGIC.equals(event.getDamageSource().getDamageType())
                && event.getDamageSource().getCausingEntity() instanceof Player caster
                && classes.hasClass(caster.getUniqueId(), PlayerClass.MAGO);
    }

    /** Fração do dano que a armadura do jogador remove, pela fórmula do jogo (de 0 a 0.8). */
    private static double armorReduction(Player player, double damage) {
        AttributeInstance armor = player.getAttribute(Attribute.ARMOR);
        AttributeInstance toughness = player.getAttribute(Attribute.ARMOR_TOUGHNESS);
        double points = armor == null ? 0 : armor.getValue();
        double hardness = toughness == null ? 0 : toughness.getValue();
        double defense = Math.min(20, Math.max(points / 5, points - damage / (2 + hardness / 4)));
        return defense / 25;
    }

    // ------------------------------------------------------------ Último Suspiro

    /** Roda por último (e antes do Totem da Imortalidade, que só é checado na morte): salva de um golpe mortal. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || EXEMPT.contains(event.getCause()) || !isCombatente(player)) {
            return;
        }
        UUID id = player.getUniqueId();
        if (fury.fury(id) < settings.lastBreathMinimum() || event.getFinalDamage() < player.getHealth()) {
            return;
        }
        event.setCancelled(true);
        fury.reset(id); // zera a Fúria e acaba com o Berserker
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double limit = max == null ? 20 : max.getValue();
        player.setHealth(Math.min(limit, settings.lastBreathHealth()));
        player.setAbsorptionAmount(settings.lastBreathAbsorption());
        player.setFireTicks(0);
        player.setNoDamageTicks(20);
        player.playEffect(EntityEffect.TOTEM_RESURRECT);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.4);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
        fury.warn(player, "§6§lÚLTIMO SUSPIRO!");
        player.sendMessage("§6§lÚLTIMO SUSPIRO!");
    }

    // ------------------------------------------------------------ arco, besta e tridente

    private static boolean isBanned(Material material) {
        return material == Material.BOW || material == Material.CROSSBOW || material == Material.TRIDENT;
    }

    private void warn(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last == null || now - last >= WARNING_COOLDOWN_MILLIS) {
            lastWarning.put(player.getUniqueId(), now);
            player.sendMessage("§cCombatentes não podem usar esta arma.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isCombatente(player) || event.getBow() == null
                || !isBanned(event.getBow().getType())) {
            return;
        }
        event.setCancelled(true);
        player.updateInventory();
        warn(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Trident trident && trident.getShooter() instanceof Player player
                && isCombatente(player)) {
            event.setCancelled(true);
            player.updateInventory();
            warn(player);
        }
    }

    /** Nem começar a puxar o arco, a besta ou o tridente: evita gastar munição e a Correnteza (que não se cancela). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        ItemStack item = event.getItem();
        if ((action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) || item == null
                || !isBanned(item.getType()) || !isCombatente(event.getPlayer())) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        warn(event.getPlayer());
    }

    /** Golpe corpo a corpo com tridente. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTridentHit(EntityDamageByEntityEvent event) {
        if (event.getCause() == DamageCause.ENTITY_ATTACK && event.getDamager() instanceof Player player
                && isCombatente(player) && player.getInventory().getItemInMainHand().getType() == Material.TRIDENT) {
            event.setCancelled(true);
            warn(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastWarning.remove(event.getPlayer().getUniqueId());
    }
}
