package br.com.murilo.muriloplug.classes.mago;

import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.teste.StatusRegistry;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Combos entre magias, num lugar só: as magias (e o Grimório) chamam estes métodos. Guarda as marcas deixadas pelo
 * Gelo e pelo Vento em cada alvo e a janela de mana grátis do Passo Arcano. Cada combo se liga/desliga no config
 * ({@code mago.combos.<combo>.ativado}) e os números ficam em {@code mago.combos.*}.
 */
public final class MagoCombos {

    private static final String BASE = "mago.combos.";
    private static final int PRUNE_SIZE = 256;

    private final JavaPlugin plugin;
    private final MagoSettings settings;
    /** Fim (ms) da marca de gelo / de vento de cada entidade. */
    private final Map<UUID, Long> iced = new HashMap<>();
    private final Map<UUID, Long> pushed = new HashMap<>();
    /** Fim (ms) da janela de mana grátis de cada Mago (Passo Arcano). */
    private final Map<UUID, Long> freeCast = new HashMap<>();

    private final StatusRegistry statuses;

    public MagoCombos(JavaPlugin plugin, MagoSettings settings, StatusRegistry statuses) {
        this.plugin = plugin;
        this.settings = settings;
        this.statuses = statuses;
    }

    // ------------------------------------------------------------ marcas

    /** O Gelo acertou o alvo: marca por alguns segundos (usada pelo Choque Térmico e pelo Condutor). */
    public void markIce(LivingEntity target) {
        mark(iced, target.getUniqueId(), settings.number(BASE + "gelo-marca-segundos"));
        statuses.add(target, "Congelado", settings.number(BASE + "gelo-marca-segundos"));
    }

    /** O Vento empurrou o alvo: marca por alguns segundos (usada pela Tempestade de Fogo). */
    public void markWind(LivingEntity target) {
        mark(pushed, target.getUniqueId(), settings.number(BASE + "vento-marca-segundos"));
        statuses.add(target, "Empurrado", settings.number(BASE + "vento-marca-segundos"));
    }

    // ------------------------------------------------------------ Choque Térmico (Gelo -> Fogo)

    /**
     * Dano que o Fogo causa neste alvo: o dobro (config) se ele estiver marcado pelo Gelo, e nesse caso a marca e o
     * efeito de gelo (lentidão e fadiga) saem na hora.
     */
    public double thermalShock(Player mage, LivingEntity target, double damage) {
        if (!enabled("choque-termico") || !active(iced, target.getUniqueId())) {
            return damage;
        }
        iced.remove(target.getUniqueId());
        statuses.remove(target, "Congelado");
        target.removePotionEffect(PotionEffectType.SLOWNESS);
        target.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        burst(target, Particle.SNOWFLAKE, Particle.FLAME);
        announce(mage, "§6§lCHOQUE TÉRMICO!");
        return damage * Math.max(1, settings.number(BASE + "choque-termico.multiplicador-dano"));
    }

    // ------------------------------------------------------------ Condutor (Gelo -> Raio)

    /** True (e avisa) se o primeiro alvo do raio está marcado pelo Gelo: o raio pula mais longe e mais vezes. */
    public boolean conductor(Player mage, LivingEntity first) {
        if (!enabled("condutor") || !active(iced, first.getUniqueId())) {
            return false;
        }
        burst(first, Particle.ELECTRIC_SPARK, Particle.SNOWFLAKE);
        announce(mage, "§e§lCONDUTOR!");
        return true;
    }

    public int conductorJumps() {
        return Math.max(0, (int) Math.round(settings.number(BASE + "condutor.saltos")));
    }

    public double conductorDistance() {
        return Math.max(0, settings.number(BASE + "condutor.distancia-salto"));
    }

    // ------------------------------------------------------------ Tempestade de Fogo (Vento -> Fogo)

    /** True se algum destes alvos está marcado pelo Vento (a explosão do Fogo vai crescer). */
    public boolean stormPending(Iterable<LivingEntity> targets) {
        if (!enabled("tempestade-fogo")) {
            return false;
        }
        for (LivingEntity target : targets) {
            if (active(pushed, target.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    public double stormRadius() {
        return Math.max(0, settings.number(BASE + "tempestade-fogo.raio"));
    }

    /** True (e avisa) se este alvo estava marcado pelo Vento: ele queima por mais tempo. A marca é consumida. */
    public boolean stormHit(Player mage, LivingEntity target) {
        if (!enabled("tempestade-fogo") || !active(pushed, target.getUniqueId())) {
            return false;
        }
        pushed.remove(target.getUniqueId());
        statuses.remove(target, "Empurrado");
        burst(target, Particle.CLOUD, Particle.FLAME);
        announce(mage, "§c§lTEMPESTADE DE FOGO!");
        return true;
    }

    public int stormBurnTicks() {
        return (int) (Math.max(0, settings.number(BASE + "tempestade-fogo.fogo-segundos")) * 20);
    }

    // ------------------------------------------------------------ Passo Arcano (Teleporte -> qualquer magia)

    /** True se a próxima magia deste Mago não gasta mana. */
    public boolean hasFreeCast(UUID mage) {
        return active(freeCast, mage);
    }

    /** Usa a mana grátis (a magia foi lançada). Retorna true se havia. */
    public boolean consumeFreeCast(UUID mage) {
        if (!active(freeCast, mage)) {
            return false;
        }
        freeCast.remove(mage);
        statuses.remove(mage, "Passo Arcano");
        return true;
    }

    /** O Mago usou a Staff de Teleporte: abre a janela da magia grátis. Retorna false se o combo está desligado. */
    public boolean armFreeCast(UUID mage) {
        if (!enabled("passo-arcano")) {
            return false;
        }
        mark(freeCast, mage, settings.number(BASE + "passo-arcano.segundos"));
        statuses.add(mage, "Passo Arcano", settings.number(BASE + "passo-arcano.segundos"));
        return true;
    }

    public void forget(UUID mage) {
        freeCast.remove(mage);
        statuses.remove(mage, "Passo Arcano");
    }

    // ------------------------------------------------------------ internos

    private boolean enabled(String combo) {
        return settings.flag(BASE + combo + ".ativado");
    }

    private void mark(Map<UUID, Long> marks, UUID id, double seconds) {
        if (marks.size() > PRUNE_SIZE) {
            long now = System.currentTimeMillis();
            marks.values().removeIf(end -> end <= now);
        }
        marks.put(id, System.currentTimeMillis() + (long) (seconds * 1000));
    }

    private static boolean active(Map<UUID, Long> marks, UUID id) {
        Long end = marks.get(id);
        if (end == null) {
            return false;
        }
        if (end <= System.currentTimeMillis()) {
            marks.remove(id);
            return false;
        }
        return true;
    }

    private static void burst(LivingEntity target, Particle first, Particle second) {
        var center = SpellTargets.bodyCenter(target);
        target.getWorld().spawnParticle(first, center, 25, 0.4, 0.6, 0.4, 0.1);
        target.getWorld().spawnParticle(second, center, 15, 0.4, 0.6, 0.4, 0.05);
    }

    /** Um tick depois, para o "-X mana" do lançamento não apagar a mensagem do combo. */
    private void announce(Player mage, String legacy) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (mage.isOnline()) {
                mage.sendActionBar(ShopUi.text(legacy));
            }
        });
    }
}
