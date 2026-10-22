package br.com.murilo.muriloplug.teste;

import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Textos em cima do boneco (TextDisplay com billboard CENTER, refeito a cada 5 ticks) e os números flutuantes de
 * dano e cura, que sobem e somem em 1 segundo. Todos os displays levam a marca do boneco para a limpeza.
 */
final class DummyHud {

    static final String HUD_MARK = "hud";
    private static final double HUD_HEIGHT = 2.75;
    private static final int NUMBER_TICKS = 20;

    private static final Map<String, String> EFFECT_NAMES = Map.ofEntries(
            Map.entry("slowness", "§bLentidão"), Map.entry("poison", "§2Veneno"),
            Map.entry("blindness", "§8Cegueira"), Map.entry("speed", "§fVelocidade"),
            Map.entry("glowing", "§eBrilho"), Map.entry("weakness", "§7Fraqueza"),
            Map.entry("mining_fatigue", "§7Fadiga"), Map.entry("jump_boost", "§aPulo"),
            Map.entry("absorption", "§6Absorção"), Map.entry("regeneration", "§dRegeneração"),
            Map.entry("resistance", "§9Resistência"), Map.entry("strength", "§cForça"),
            Map.entry("hunger", "§2Fome"), Map.entry("nausea", "§2Enjoo"),
            Map.entry("levitation", "§fLevitação"), Map.entry("invisibility", "§7Invisibilidade"),
            Map.entry("fire_resistance", "§6Res. ao Fogo"), Map.entry("wither", "§8Wither"),
            Map.entry("darkness", "§8Escuridão"), Map.entry("haste", "§eRapidez"),
            Map.entry("night_vision", "§9Visão Noturna"), Map.entry("water_breathing", "§3Resp. Aquática"),
            Map.entry("instant_health", "§cCura"), Map.entry("instant_damage", "§4Dano"),
            Map.entry("slow_falling", "§fQueda Lenta"), Map.entry("bad_omen", "§4Mau Agouro"));
    private static final Map<String, String> STATUS_COLORS = Map.of(
            "Preso", "§5", "Provocado", "§6", "Vinculado", "§d", "Congelado", "§b", "Empurrado", "§f",
            "Passo Arcano", "§3");

    private final JavaPlugin plugin;
    private final NamespacedKey key;
    private final DummySettings settings;
    private final StatusRegistry statuses;
    private final Set<TextDisplay> numbers = new HashSet<>();

    DummyHud(JavaPlugin plugin, NamespacedKey key, DummySettings settings, StatusRegistry statuses) {
        this.plugin = plugin;
        this.key = key;
        this.settings = settings;
        this.statuses = statuses;
    }

    private TextDisplay spawnDisplay(Location at, String legacy) {
        return at.getWorld().spawn(at, TextDisplay.class, text -> {
            text.setBillboard(Display.Billboard.CENTER);
            text.setPersistent(false);
            text.setLineWidth(400);
            text.setViewRange(1.0f);
            text.getPersistentDataContainer().set(key, PersistentDataType.STRING, HUD_MARK);
            text.text(ShopUi.text(legacy));
        });
    }

    // ------------------------------------------------------------ texto fixo

    /** Cria o display (se faltar), reposiciona e refaz o texto quando mudou. */
    void update(Dummy dummy) {
        Zombie body = dummy.entity;
        Location at = body.getLocation().add(0, HUD_HEIGHT, 0);
        String text = text(dummy);
        if (dummy.display == null || !dummy.display.isValid()) {
            dummy.display = spawnDisplay(at, text);
            dummy.lastText = text;
            return;
        }
        if (!text.equals(dummy.lastText)) {
            dummy.display.text(ShopUi.text(text));
            dummy.lastText = text;
        }
        dummy.display.teleport(at);
    }

    private String text(Dummy dummy) {
        LivingEntity body = dummy.entity;
        StringBuilder text = new StringBuilder();
        text.append(dummy.type == TargetRelations.DummyType.INIMIGO
                ? "§c§l[INIMIGO] §fBoneco de Teste" : "§a§l[ALIADO] §fBoneco de Teste");
        text.append("\n§c❤ §f").append(format(body.getHealth())).append('/').append((int) maxHealth(body));
        if (body.getAbsorptionAmount() > 0) {
            text.append(" §6+").append(format(body.getAbsorptionAmount()));
        }
        text.append('\n').append(effectsLine(body));
        String status = statusLine(body);
        if (!status.isEmpty()) {
            text.append('\n').append(status);
        }
        text.append("\n§7Dano nos últimos ").append((int) (settings.damageWindowMillis() / 1000)).append("s: §f")
                .append(format(recentDamage(dummy)));
        return text.toString();
    }

    private static double maxHealth(LivingEntity body) {
        var max = body.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return max == null ? 20 : max.getValue();
    }

    private String effectsLine(LivingEntity body) {
        List<String> parts = new ArrayList<>();
        for (PotionEffect effect : body.getActivePotionEffects()) {
            String id = effect.getType().getKey().getKey();
            String name = EFFECT_NAMES.getOrDefault(id, "§f" + id.replace('_', ' '));
            String seconds = effect.isInfinite() ? "∞" : String.valueOf((int) Math.ceil(effect.getDuration() / 20.0));
            parts.add(name + " " + roman(effect.getAmplifier() + 1) + " §7(" + seconds + "s)");
        }
        return parts.isEmpty() ? "§7Sem efeitos" : String.join(" ", parts);
    }

    private String statusLine(LivingEntity body) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Double> status : statuses.get(body).entrySet()) {
            String color = STATUS_COLORS.getOrDefault(status.getKey(), "§e");
            boolean timed = status.getValue() < StatusRegistry.INDEFINITE / 2;
            parts.add(color + status.getKey() + (timed
                    ? " §7(" + String.format(Locale.ROOT, "%.1f", status.getValue()) + "s)" : ""));
        }
        return String.join(" ", parts);
    }

    private static String roman(int level) {
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level >= 1 && level < numerals.length ? numerals[level] : String.valueOf(level);
    }

    static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    // ------------------------------------------------------------ dano recente

    void recordDamage(Dummy dummy, double amount) {
        dummy.recent.addLast(new double[]{System.currentTimeMillis(), amount});
    }

    private double recentDamage(Dummy dummy) {
        long limit = System.currentTimeMillis() - settings.damageWindowMillis();
        while (!dummy.recent.isEmpty() && dummy.recent.peekFirst()[0] < limit) {
            dummy.recent.removeFirst();
        }
        double total = 0;
        for (double[] hit : dummy.recent) {
            total += hit[1];
        }
        return total;
    }

    // ------------------------------------------------------------ números flutuantes

    /** Texto que sobe a partir do corpo do boneco e some em 1 segundo. */
    void number(Dummy dummy, String legacy) {
        Zombie body = dummy.entity;
        if (body == null || !body.isValid()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location start = body.getLocation().add(random.nextDouble(-0.4, 0.4), 1.4, random.nextDouble(-0.4, 0.4));
        World world = start.getWorld();
        if (world == null) {
            return;
        }
        TextDisplay display = spawnDisplay(start, legacy);
        numbers.add(display);
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!display.isValid() || tick++ >= NUMBER_TICKS) {
                    display.remove();
                    numbers.remove(display);
                    cancel();
                    return;
                }
                display.teleport(display.getLocation().add(0, 0.04, 0));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Remove todos os números ainda no ar (desligamento do plugin). */
    void removeAllNumbers() {
        for (TextDisplay display : new ArrayList<>(numbers)) {
            display.remove();
        }
        numbers.clear();
    }
}
