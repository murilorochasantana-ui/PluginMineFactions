package br.com.murilo.muriloplug.teste;

import br.com.murilo.muriloplug.combat.CombatManager;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Eventos dos bonecos de teste: mostra o dano (número e action bar de quem causou), impede a morte ("RESET"), poupa o
 * boneco aliado de golpes e habilidades, ignora sufocamento, afogamento e esmagamento e proíbe qualquer interação.
 * Deve ser registrado por último: o cancelamento do dano letal acontece em MONITOR, depois dos outros sistemas
 * (Fúria, Golpe Pulando) terem visto o golpe.
 */
public final class DummyListener implements Listener {

    private static final Set<DamageCause> IMMUNE = Set.of(DamageCause.SUFFOCATION, DamageCause.DROWNING, DamageCause.CRAMMING);
    private static final Set<DamageCause> AREA = Set.of(
            DamageCause.ENTITY_EXPLOSION, DamageCause.BLOCK_EXPLOSION, DamageCause.ENTITY_SWEEP_ATTACK);
    private static final Set<DamageCause> OVER_TIME = Set.of(
            DamageCause.POISON, DamageCause.FIRE, DamageCause.FIRE_TICK, DamageCause.LAVA, DamageCause.WITHER,
            DamageCause.HOT_FLOOR);

    private final JavaPlugin plugin;
    private final DummyManager manager;
    private final DummyHud hud;
    private final DummySettings settings;
    private final CombatManager combat;
    private final BooleanSupplier areaDamage;

    /** {@code areaDamage}: true enquanto uma habilidade aplica dano em área (para colorir o número). */
    public DummyListener(JavaPlugin plugin, DummyManager manager, DummySettings settings, CombatManager combat,
                         BooleanSupplier areaDamage) {
        this.plugin = plugin;
        this.manager = manager;
        this.hud = manager.hud();
        this.settings = settings;
        this.combat = combat;
        this.areaDamage = areaDamage;
    }

    private boolean isAlly(Dummy dummy) {
        return dummy != null && dummy.type == TargetRelations.DummyType.ALIADO;
    }

    // ------------------------------------------------------------ dano

    /** O boneco aliado não leva golpes nem habilidades (como um membro do clan); só o /boneco dano o machuca. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHitAlly(EntityDamageByEntityEvent event) {
        if (isAlly(manager.find(event.getEntity()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onImmune(EntityDamageEvent event) {
        if (IMMUNE.contains(event.getCause()) && manager.find(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Dummy dummy = manager.find(event.getEntity());
        if (dummy == null) {
            return;
        }
        double damage = event.getFinalDamage();
        if (damage <= 0) {
            return;
        }
        hud.recordDamage(dummy, damage);
        hud.number(dummy, color(event) + "-" + DummyHud.format(damage));
        Player causer = event.getDamageSource().getCausingEntity() instanceof Player player ? player : null;
        if (causer != null) {
            causer.sendActionBar(br.com.murilo.muriloplug.shop.ShopUi.text(
                    "§7Boneco: §c-" + DummyHud.format(damage) + " §8(" + kind(event) + ")"));
            if (settings.combatOnHit() && dummy.type == TargetRelations.DummyType.INIMIGO) {
                combat.engage(causer);
            }
        }
        // Nunca morre: o dano que levaria a vida a 0 volta o boneco para o máximo.
        if (damage >= dummy.entity.getHealth()) {
            event.setCancelled(true);
            manager.resetHealth(dummy);
            hud.number(dummy, "§c§lRESET");
        }
    }

    private String color(EntityDamageEvent event) {
        if (areaDamage.getAsBoolean() || AREA.contains(event.getCause())) {
            return "§e";
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.isCritical()) {
            return "§6";
        }
        if (OVER_TIME.contains(event.getCause())) {
            return "§2";
        }
        DamageType type = event.getDamageSource().getDamageType();
        if (event.getCause() == DamageCause.MAGIC || DamageType.MAGIC.equals(type) || DamageType.INDIRECT_MAGIC.equals(type)) {
            return "§5";
        }
        return "§c";
    }

    private String kind(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.isCritical()) {
            return "crítico";
        }
        return switch (event.getCause()) {
            case ENTITY_ATTACK -> "golpe";
            case ENTITY_SWEEP_ATTACK -> "golpe em área";
            case PROJECTILE -> "projétil";
            case MAGIC -> "magia";
            case POISON -> "veneno";
            case FIRE, FIRE_TICK, LAVA -> "fogo";
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> "explosão";
            case FALL -> "queda";
            case CUSTOM -> "comando";
            default -> event.getCause().name().toLowerCase(java.util.Locale.ROOT);
        };
    }

    // ------------------------------------------------------------ sem morte, sem IA, sem interação

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (manager.find(event.getEntity()) != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (manager.find(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    /** Na água o Zombie viraria Drowned. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (manager.find(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (manager.find(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (manager.find(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (manager.find(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    /** Bonecos e textos que sobraram de um desligamento brusco saem quando o chunk carrega. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (manager.isMarked(entity) && !manager.owns(entity)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (entity.isValid() && !manager.owns(entity)) {
                        entity.remove();
                    }
                });
            }
        }
    }
}
