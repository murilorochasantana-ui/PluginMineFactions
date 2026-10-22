package br.com.murilo.muriloplug.teste;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.RayTraceResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Bonecos de teste: um Zombie adulto sem IA (o Mannequin só existe em versões mais novas que a 1.21.4) que recebe
 * dano, cura e efeitos como um jogador, nunca morre e volta sozinho para o lugar. Os bonecos ficam em bonecos.yml e o
 * Zombie não é salvo no chunk: um relógio recria quem faltar (chunk que carrega, boneco morto por /kill etc.), e as
 * sobras de um desligamento brusco são removidas ao ligar e quando o chunk carrega.
 */
public final class DummyManager {

    private static final long TICK_PERIOD = 5L;
    /** Confere se falta algum boneco a cada 4 execuções (1 segundo). */
    private static final int ENSURE_EVERY = 4;
    private static final double AWAY_DISTANCE_SQUARED = 0.36;
    private static final double MOVED_DISTANCE_SQUARED = 0.0025;
    public static final double MAX_HEALTH = 20;

    private final JavaPlugin plugin;
    private final DummySettings settings;
    private final TargetRelations relations;
    private final StatusRegistry statuses;
    private final DummyHud hud;
    private final File file;
    private final List<Dummy> dummies = new ArrayList<>();
    private int ticks;

    public DummyManager(JavaPlugin plugin, DummySettings settings, TargetRelations relations, StatusRegistry statuses) {
        this.plugin = plugin;
        this.settings = settings;
        this.relations = relations;
        this.statuses = statuses;
        this.hud = new DummyHud(plugin, relations.dummyKey(), settings, statuses);
        this.file = new File(plugin.getDataFolder(), "bonecos.yml");
    }

    DummyHud hud() {
        return hud;
    }

    // ------------------------------------------------------------ ciclo de vida

    /** Remove as sobras, recria os bonecos salvos e liga o relógio. */
    public void start() {
        sweep();
        load();
        if (settings.enabled()) {
            dummies.forEach(this::spawn);
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    /** Desligamento do plugin: bonecos, textos e números saem do mundo (os bonecos voltam do arquivo ao ligar). */
    public void shutdown() {
        for (Dummy dummy : dummies) {
            despawn(dummy);
        }
        hud.removeAllNumbers();
        sweep();
    }

    /** Remove todas as entidades marcadas como boneco ou texto de boneco dos mundos carregados. */
    private void sweep() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (isMarked(entity)) {
                    entity.remove();
                }
            }
        }
    }

    boolean isMarked(Entity entity) {
        return entity.getPersistentDataContainer().has(relations.dummyKey(), PersistentDataType.STRING);
    }

    /** True se a entidade é de um boneco que o plugin está controlando agora. */
    boolean owns(Entity entity) {
        for (Dummy dummy : dummies) {
            if ((dummy.entity != null && dummy.entity.equals(entity))
                    || (dummy.display != null && dummy.display.equals(entity))) {
                return true;
            }
        }
        return false;
    }

    private void despawn(Dummy dummy) {
        if (dummy.entity != null) {
            dummy.entity.remove();
            dummy.entity = null;
        }
        if (dummy.display != null) {
            dummy.display.remove();
            dummy.display = null;
        }
    }

    // ------------------------------------------------------------ criação e remoção

    /** Cria um boneco na posição, com a armadura "nenhuma". */
    public Dummy create(TargetRelations.DummyType type, Location at) {
        Dummy dummy = Dummy.at(type, at, "nenhuma");
        dummies.add(dummy);
        spawn(dummy);
        save();
        return dummy;
    }

    public void remove(Dummy dummy) {
        despawn(dummy);
        dummies.remove(dummy);
        save();
    }

    public int removeAll() {
        int count = dummies.size();
        for (Dummy dummy : dummies) {
            despawn(dummy);
        }
        dummies.clear();
        save();
        return count;
    }

    private void spawn(Dummy dummy) {
        Location at = dummy.origin();
        if (at == null || !at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        double health = dummy.type == TargetRelations.DummyType.ALIADO ? settings.allyStartHealth() : MAX_HEALTH;
        Zombie body = at.getWorld().spawn(at, Zombie.class, zombie -> {
            zombie.setBaby(false);
            zombie.setShouldBurnInDay(false);
            zombie.setCanBreakDoors(false);
            zombie.setCanPickupItems(false);
            zombie.setSilent(true);
            zombie.setCollidable(false);
            zombie.setRemoveWhenFarAway(false);
            zombie.setPersistent(false);
            zombie.setCustomNameVisible(false);
            zombie.getPersistentDataContainer().set(relations.dummyKey(), PersistentDataType.STRING, dummy.type.id());
            AttributeInstance max = zombie.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(MAX_HEALTH);
            }
            AttributeInstance reinforcements = zombie.getAttribute(Attribute.SPAWN_REINFORCEMENTS);
            if (reinforcements != null) {
                reinforcements.setBaseValue(0);
            }
            zombie.setHealth(health);
        });
        // Sem objetivos de IA, mas com a física: pode ser empurrado e puxado.
        Bukkit.getMobGoals().removeAllGoals(body);
        body.setTarget(null);
        dummy.entity = body;
        dummy.lastHealth = health;
        dummy.lastPosition = body.getLocation();
        dummy.stillSince = 0;
        dummy.fullSince = 0;
        dummy.lastText = "";
        applyArmor(dummy);
        // O jogo pode dar equipamento aleatório depois do nascimento: confere de novo no tick seguinte.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (dummy.entity != null && dummy.entity.isValid()) {
                applyArmor(dummy);
            }
        });
    }

    private void applyArmor(Dummy dummy) {
        EntityEquipment equipment = dummy.entity.getEquipment();
        Material[] set = Dummy.armorSet(dummy.armor);
        equipment.setHelmet(set == null ? null : new ItemStack(set[0]));
        equipment.setChestplate(set == null ? null : new ItemStack(set[1]));
        equipment.setLeggings(set == null ? null : new ItemStack(set[2]));
        equipment.setBoots(set == null ? null : new ItemStack(set[3]));
        equipment.setItemInMainHand(null);
        equipment.setItemInOffHand(null);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            equipment.setDropChance(slot, 0f);
        }
    }

    // ------------------------------------------------------------ consulta

    public Dummy find(Entity entity) {
        for (Dummy dummy : dummies) {
            if (dummy.entity != null && dummy.entity.getUniqueId().equals(entity.getUniqueId())) {
                return dummy;
            }
        }
        return null;
    }

    /** Boneco na mira do jogador (até {@code range} blocos, sem atravessar parede), ou null. */
    public Dummy aimed(Player player, double range) {
        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER,
                true, 0.5, entity -> find(entity) != null);
        return hit == null || hit.getHitEntity() == null ? null : find(hit.getHitEntity());
    }

    /** Boneco mais próximo do jogador, até {@code range} blocos, ou null. */
    public Dummy nearest(Player player, double range) {
        Dummy best = null;
        double bestDistance = range * range;
        for (Dummy dummy : dummies) {
            if (dummy.entity == null || !dummy.entity.isValid() || !dummy.entity.getWorld().equals(player.getWorld())) {
                continue;
            }
            double distance = dummy.entity.getLocation().distanceSquared(player.getLocation());
            if (distance <= bestDistance) {
                best = dummy;
                bestDistance = distance;
            }
        }
        return best;
    }

    public int count() {
        return dummies.size();
    }

    // ------------------------------------------------------------ comandos

    /** Define a vida (já limitada pelo comando) e esquece o "ficou cheio". */
    public void setHealth(Dummy dummy, double value) {
        dummy.entity.setHealth(Math.min(value, MAX_HEALTH));
        dummy.lastHealth = dummy.entity.getHealth();
        dummy.fullSince = 0;
    }

    /** O dano que levaria o boneco a 0: ele não morre, volta ao máximo. */
    public void resetHealth(Dummy dummy) {
        setHealth(dummy, MAX_HEALTH);
    }

    public void setArmor(Dummy dummy, String armor) {
        dummy.armor = armor;
        applyArmor(dummy);
        save();
    }

    /** 20 de vida, sem efeitos, sem marcas, parado e na posição original. */
    public void reset(Dummy dummy) {
        Zombie body = dummy.entity;
        for (PotionEffect effect : new ArrayList<>(body.getActivePotionEffects())) {
            body.removePotionEffect(effect.getType());
        }
        statuses.clear(body);
        body.setFireTicks(0);
        body.setAbsorptionAmount(0);
        body.setNoDamageTicks(0);
        Location origin = dummy.origin();
        if (origin != null) {
            body.teleport(origin);
        }
        body.setVelocity(new org.bukkit.util.Vector());
        setHealth(dummy, MAX_HEALTH);
        dummy.recent.clear();
        dummy.stillSince = 0;
    }

    // ------------------------------------------------------------ relógio

    private void tick() {
        if (!settings.enabled()) {
            for (Dummy dummy : dummies) {
                despawn(dummy);
            }
            return;
        }
        boolean ensure = ++ticks % ENSURE_EVERY == 0;
        long now = System.currentTimeMillis();
        for (Dummy dummy : new ArrayList<>(dummies)) {
            if (dummy.entity == null || !dummy.entity.isValid()) {
                dummy.entity = null;
                if (dummy.display != null) {
                    dummy.display.remove();
                    dummy.display = null;
                }
                if (ensure) {
                    spawn(dummy);
                }
                continue;
            }
            returnToOrigin(dummy, now);
            allyReset(dummy, now);
            detectHeal(dummy);
            hud.update(dummy);
        }
    }

    /** Fora do lugar e parado por 3s (e não preso): volta sozinho para a posição original. */
    private void returnToOrigin(Dummy dummy, long now) {
        Zombie body = dummy.entity;
        Location origin = dummy.origin();
        Location here = body.getLocation();
        boolean moved = dummy.lastPosition == null || !dummy.lastPosition.getWorld().equals(here.getWorld())
                || dummy.lastPosition.distanceSquared(here) > MOVED_DISTANCE_SQUARED;
        dummy.lastPosition = here;
        boolean away = origin != null && (!origin.getWorld().equals(here.getWorld())
                || origin.distanceSquared(here) > AWAY_DISTANCE_SQUARED);
        if (!away || moved || statuses.has(body, "Preso")) {
            dummy.stillSince = 0;
            return;
        }
        if (dummy.stillSince == 0) {
            dummy.stillSince = now;
        } else if (now - dummy.stillSince >= settings.returnMillis()) {
            body.getWorld().spawnParticle(Particle.POOF, here.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
            body.teleport(origin);
            body.setVelocity(new org.bukkit.util.Vector());
            dummy.lastPosition = origin;
            dummy.stillSince = 0;
        }
    }

    /** Aliado com a vida cheia por 3s volta para a vida inicial (para testar a cura de novo). */
    private void allyReset(Dummy dummy, long now) {
        if (dummy.type != TargetRelations.DummyType.ALIADO) {
            return;
        }
        Zombie body = dummy.entity;
        if (body.getHealth() < MAX_HEALTH - 0.01) {
            dummy.fullSince = 0;
            return;
        }
        if (dummy.fullSince == 0) {
            dummy.fullSince = now;
        } else if (now - dummy.fullSince >= settings.allyResetMillis()) {
            setHealth(dummy, settings.allyStartHealth());
        }
    }

    /** Vida que subiu desde a última conferência (Coração do Mar, Regeneração...): mostra "+cura". */
    private void detectHeal(Dummy dummy) {
        double health = dummy.entity.getHealth();
        double gained = health - dummy.lastHealth;
        dummy.lastHealth = health;
        if (gained > 0.05) {
            hud.number(dummy, "§a+" + DummyHud.format(gained));
        }
    }

    // ------------------------------------------------------------ arquivo

    private void load() {
        dummies.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("lista");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            TargetRelations.DummyType type = section == null ? null : TargetRelations.DummyType.byId(section.getString("tipo"));
            if (type == null || section.getString("mundo") == null) {
                plugin.getLogger().warning("bonecos.yml: boneco inválido ignorado: " + key);
                continue;
            }
            if (Bukkit.getWorld(section.getString("mundo")) == null) {
                plugin.getLogger().warning("bonecos.yml: o mundo '" + section.getString("mundo")
                        + "' não existe; boneco ignorado.");
                continue;
            }
            String armor = section.getString("armadura", "nenhuma");
            dummies.add(new Dummy(type, section.getString("mundo"), section.getDouble("x"), section.getDouble("y"),
                    section.getDouble("z"), (float) section.getDouble("yaw"), (float) section.getDouble("pitch"),
                    Dummy.armorSet(armor) == null ? "nenhuma" : armor));
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (int i = 0; i < dummies.size(); i++) {
            Dummy dummy = dummies.get(i);
            String base = "lista." + i + ".";
            yaml.set(base + "tipo", dummy.type.id());
            yaml.set(base + "mundo", dummy.world);
            yaml.set(base + "x", dummy.x);
            yaml.set(base + "y", dummy.y);
            yaml.set(base + "z", dummy.z);
            yaml.set(base + "yaw", dummy.yaw);
            yaml.set(base + "pitch", dummy.pitch);
            yaml.set(base + "armadura", dummy.armor);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar bonecos.yml: " + e.getMessage());
        }
    }
}
