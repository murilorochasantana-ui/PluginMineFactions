package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.teste.StatusRegistry;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Purificar (Shift + clique esquerdo com o Coração do Mar): tira os efeitos ruins do Suporte e dos aliados em volta,
 * as poções negativas e as marcas de controle do {@link StatusRegistry}, terminando também o efeito real de cada
 * marca (quem aplica a marca ensina como desfazê-la com {@link StatusRegistry#registerCleanser}).
 */
public final class Purificar {

    /** Poções negativas que saem. */
    private static final List<PotionEffectType> BAD_EFFECTS = List.of(
            PotionEffectType.POISON, PotionEffectType.SLOWNESS, PotionEffectType.BLINDNESS, PotionEffectType.WEAKNESS,
            PotionEffectType.MINING_FATIGUE, PotionEffectType.WITHER, PotionEffectType.NAUSEA, PotionEffectType.HUNGER,
            PotionEffectType.DARKNESS, PotionEffectType.LEVITATION, PotionEffectType.UNLUCK, PotionEffectType.BAD_OMEN);
    /** Marcas negativas do StatusRegistry que saem. */
    public static final List<String> BAD_STATUSES = List.of(
            "Preso", "Marcado", "Congelado", "Provocado", "Silenciado", "Amaldiçoado", "Cego", "Empurrado");
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.WHITE, 1.4f);

    private final SuporteSettings settings;
    private final SuporteTargets targets;
    private final SuporteAssists assists;
    private final StatusRegistry statuses;

    public Purificar(SuporteSettings settings, SuporteTargets targets, SuporteAssists assists,
                     StatusRegistry statuses) {
        this.settings = settings;
        this.targets = targets;
        this.assists = assists;
        this.statuses = statuses;
    }

    /** Sempre acontece (mesmo sem nada para tirar). */
    public boolean cast(Player support) {
        int cleaned = 0;
        for (LivingEntity target : targets.group(support, settings.purifyRadius())) {
            boolean any = false;
            for (PotionEffectType type : BAD_EFFECTS) {
                if (target.hasPotionEffect(type)) {
                    target.removePotionEffect(type);
                    any = true;
                }
            }
            any |= !statuses.cleanse(target, BAD_STATUSES).isEmpty();
            if (any) {
                cleaned++;
            }
            target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, target.getHeight() / 2, 0), 25,
                    0.4, 0.7, 0.4, 0, WHITE);
            target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 10, 0.3, 0.6, 0.3, 0.03);
            if (target instanceof Player ally && !ally.equals(support)) {
                assists.record(support.getUniqueId(), ally.getUniqueId());
                if (any) {
                    ally.sendActionBar(ShopUi.text("§fVocê foi purificado por " + support.getName() + "."));
                }
            }
        }
        support.getWorld().playSound(support.getLocation(), Sound.BLOCK_BELL_USE, 1f, 1.4f);
        support.getWorld().playSound(support.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        support.sendActionBar(ShopUi.text("§fPurificar: " + cleaned + " purificado(s)."));
        return true;
    }
}
