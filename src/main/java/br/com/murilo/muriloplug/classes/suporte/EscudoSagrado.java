package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.teste.StatusRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Escudo Sagrado (clique direito com o Escudo): absorção no aliado na mira (ou no próprio Suporte) por alguns
 * segundos, com um escudo azul translúcido em volta dele, visível para todos.
 *
 * <p>Como o visual funciona: um {@link BlockDisplay} de vidro azul-claro (LIGHT_BLUE_STAINED_GLASS), esticado para
 * envolver o corpo inteiro (um pouco mais largo e mais alto que a entidade) e centralizado nos pés dela. O vidro
 * colorido é translúcido, então quem está de fora vê o jogador por dentro; quem está dentro não é atrapalhado, porque
 * as faces do bloco só são desenhadas pelo lado de fora. A cada tick o display é teleportado para a posição do
 * aliado, com {@code teleportDuration} de 1 tick para o cliente suavizar o movimento; a cada 5 ticks sai também um
 * anel de partículas azuis na altura da cintura. O escudo some quando a absorção chega a 0 (quebrado, com som e
 * cacos de vidro), quando o tempo acaba, quando o aliado sai ou morre e no onDisable. Os displays são marcados no
 * PersistentDataContainer (mineplug:suporte_escudo) e não são salvos no mundo; se sobrar algum de um desligamento
 * brusco, ele é apagado quando o chunk carrega.
 */
public final class EscudoSagrado implements Listener {

    public static final String STATUS = "Escudo Sagrado";
    private static final double ABSORPTION_PER_LEVEL = 4;
    private static final float PADDING_SIDE = 0.45f;
    private static final float PADDING_TOP = 0.25f;
    private static final Particle.DustOptions RING = new Particle.DustOptions(Color.fromRGB(110, 190, 255), 1.0f);

    private static final class Shield {
        final UUID target;
        final BlockDisplay display;
        final long until;
        final int amplifier;
        final float width;

        Shield(UUID target, BlockDisplay display, long until, int amplifier, float width) {
            this.target = target;
            this.display = display;
            this.until = until;
            this.amplifier = amplifier;
            this.width = width;
        }
    }

    private final JavaPlugin plugin;
    private final SuporteSettings settings;
    private final SuporteTargets targets;
    private final SuporteAssists assists;
    private final StatusRegistry statuses;
    private final NamespacedKey markKey;
    /** Escudo ativo de cada entidade protegida. */
    private final Map<UUID, Shield> shields = new HashMap<>();
    private int ticks;

    public EscudoSagrado(JavaPlugin plugin, SuporteSettings settings, SuporteTargets targets, SuporteAssists assists,
                         StatusRegistry statuses) {
        this.plugin = plugin;
        this.settings = settings;
        this.targets = targets;
        this.assists = assists;
        this.statuses = statuses;
        this.markKey = new NamespacedKey(plugin, "suporte_escudo");
    }

    public void start() {
        removeLeftovers();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Tira todos os escudos (onDisable). */
    public void endAll() {
        for (UUID id : List.copyOf(shields.keySet())) {
            end(id, false);
        }
        removeLeftovers();
    }

    /** Aliado na mira (sem atravessar parede) ou, sem aliado na mira, o próprio Suporte. Sempre acontece. */
    public boolean cast(Player support) {
        LivingEntity aimed = targets.aimedTarget(support, settings.shieldRange());
        LivingEntity target = aimed != null && targets.isAlly(support, aimed) ? aimed : support;
        apply(target);
        support.getWorld().playSound(support.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1f, 1.3f);
        if (target.equals(support)) {
            support.sendActionBar(ShopUi.text("§bEscudo Sagrado em você."));
        } else {
            if (target instanceof Player ally) {
                assists.record(support.getUniqueId(), ally.getUniqueId());
                ally.sendActionBar(ShopUi.text("§bVocê recebeu o Escudo Sagrado de " + support.getName() + "."));
            }
            support.sendActionBar(ShopUi.text("§bEscudo Sagrado no aliado."));
        }
        return true;
    }

    private void apply(LivingEntity target) {
        end(target.getUniqueId(), false); // renovar: o escudo anterior sai sem o som de quebrar
        double absorption = settings.shieldAbsorption();
        double seconds = settings.shieldSeconds();
        int amplifier = Math.max(0, (int) Math.ceil(absorption / ABSORPTION_PER_LEVEL) - 1);
        // O efeito de Absorção libera a vida extra (atributo de absorção máxima); ao acabar, ele mesmo a tira.
        target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, (int) Math.ceil(seconds * 20), amplifier,
                false, false, true));
        target.setAbsorptionAmount(absorption);
        float width = (float) target.getWidth() + PADDING_SIDE * 2;
        float height = (float) target.getHeight() + PADDING_TOP;
        BlockDisplay display = target.getWorld().spawn(target.getLocation(), BlockDisplay.class, d -> {
            d.setBlock(Material.LIGHT_BLUE_STAINED_GLASS.createBlockData());
            d.setTransformation(new Transformation(new Vector3f(-width / 2, -0.05f, -width / 2), new AxisAngle4f(),
                    new Vector3f(width, height, width), new AxisAngle4f()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setPersistent(false);
            d.getPersistentDataContainer().set(markKey, PersistentDataType.BYTE, (byte) 1);
        });
        shields.put(target.getUniqueId(), new Shield(target.getUniqueId(), display,
                System.currentTimeMillis() + (long) (seconds * 1000), amplifier, width));
        statuses.add(target, STATUS, seconds);
        target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, target.getHeight() / 2, 0), 25,
                0.5, 0.8, 0.5, 0, RING);
    }

    private void tick() {
        if (shields.isEmpty()) {
            return;
        }
        ticks++;
        long now = System.currentTimeMillis();
        for (Shield shield : List.copyOf(shields.values())) {
            Entity found = Bukkit.getEntity(shield.target);
            if (!(found instanceof LivingEntity target) || !target.isValid() || target.isDead()
                    || !shield.display.isValid()) {
                end(shield.target, false); // saiu, morreu ou o display sumiu
            } else if (target.getAbsorptionAmount() <= 0) {
                end(shield.target, true); // os corações dourados acabaram: escudo quebrado
            } else if (shield.until <= now) {
                end(shield.target, true);
            } else {
                follow(shield, target);
            }
        }
    }

    private void follow(Shield shield, LivingEntity target) {
        Location at = target.getLocation();
        at.setYaw(0);
        at.setPitch(0);
        if (!shield.display.getWorld().equals(at.getWorld())) {
            shield.display.teleport(at);
        } else if (shield.display.getLocation().distanceSquared(at) > 1.0E-4) {
            shield.display.teleport(at);
        }
        if (ticks % 5 == 0) {
            ring(target, shield.width / 2);
        }
    }

    /** Anel de partículas azuis em volta da cintura. */
    private static void ring(LivingEntity target, double radius) {
        World world = target.getWorld();
        Location center = target.getLocation().add(0, target.getHeight() * 0.55, 0);
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8;
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius),
                    1, 0, 0, 0, 0, RING);
        }
    }

    /** Tira o escudo. {@code broken}: com o som e os cacos de vidro de "quebrar". */
    private void end(UUID id, boolean broken) {
        Shield shield = shields.remove(id);
        if (shield == null) {
            return;
        }
        statuses.remove(id, STATUS);
        Location where = shield.display.isValid() ? shield.display.getLocation() : null;
        shield.display.remove();
        Entity found = Bukkit.getEntity(id);
        if (found instanceof LivingEntity target) {
            where = target.getLocation();
            // Só tira a Absorção que é deste escudo (mesmo nível e ainda dentro do tempo dele).
            PotionEffect effect = target.getPotionEffect(PotionEffectType.ABSORPTION);
            if (effect != null && effect.getAmplifier() == shield.amplifier
                    && effect.getDuration() <= Math.ceil(settings.shieldSeconds() * 20)) {
                target.removePotionEffect(PotionEffectType.ABSORPTION);
            }
        }
        if (broken && where != null) {
            World world = where.getWorld();
            Location center = where.clone().add(0, 1, 0);
            world.playSound(center, Sound.ITEM_SHIELD_BREAK, 1f, 1.2f);
            world.playSound(center, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.4f);
            world.spawnParticle(Particle.BLOCK, center, 40, 0.4, 0.7, 0.4, 0,
                    Material.LIGHT_BLUE_STAINED_GLASS.createBlockData());
        }
    }

    // ------------------------------------------------------------ sobras

    private boolean isMarked(Entity entity) {
        return entity.getPersistentDataContainer().has(markKey, PersistentDataType.BYTE);
    }

    private boolean owns(Entity entity) {
        for (Shield shield : shields.values()) {
            if (shield.display.equals(entity)) {
                return true;
            }
        }
        return false;
    }

    private void removeLeftovers() {
        for (World world : Bukkit.getWorlds()) {
            for (BlockDisplay display : world.getEntitiesByClass(BlockDisplay.class)) {
                if (isMarked(display) && !owns(display)) {
                    display.remove();
                }
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof BlockDisplay && isMarked(entity) && !owns(entity)) {
                entity.remove();
            }
        }
    }
}
