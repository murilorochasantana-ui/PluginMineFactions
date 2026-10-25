package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Linhas do sidebar do Engenheiro (null para quem não é Engenheiro). Para uma habilidade nova (Golems, Gancho,
 * Sentinelas...), crie um método {@code Component xxxLine(Player)} aqui e ligue-o na lista do SidebarManager.
 */
public final class EngenheiroSidebar {

    private final ClassManager classes;
    private final EngenheiroEnergia energy;
    private final EngenheiroSettings settings;
    private final GanchoManager hook;
    private final SentinelaManager sentinels;
    private final GolemManager golems;

    public EngenheiroSidebar(ClassManager classes, EngenheiroEnergia energy, EngenheiroSettings settings,
                             GanchoManager hook, SentinelaManager sentinels, GolemManager golems) {
        this.hook = hook;
        this.sentinels = sentinels;
        this.golems = golems;
        this.classes = classes;
        this.energy = energy;
        this.settings = settings;
    }

    private boolean isEngineer(Player player) {
        return classes.hasClass(player.getUniqueId(), PlayerClass.ENGENHEIRO);
    }

    /** "Energia: 42/100". */
    public Component energyLine(Player player) {
        if (!isEngineer(player)) {
            return null;
        }
        UUID id = player.getUniqueId();
        return ShopUi.text("§bEnergia: §f" + (int) energy.getEnergia(id) + "/" + (int) energy.maxEnergia());
    }

    /** "Gancho [Besta] ✔" (ou ✖ 2s durante a recarga). */
    public Component hookLine(Player player) {
        if (!isEngineer(player)) {
            return null;
        }
        int wait = hook.remainingSeconds(player.getUniqueId());
        Component line = ShopUi.text("§7Gancho §7[Besta] ").append(mark(wait == 0));
        return wait > 0 ? line.append(ShopUi.text(" §c" + wait + "s")) : line;
    }

    /** "Sentinelas 1/3". */
    public Component sentinelLine(Player player) {
        if (!isEngineer(player)) {
            return null;
        }
        return ShopUi.text("§cSentinelas §f" + sentinels.count(player.getUniqueId()) + "/" + settings.sentinelMax());
    }

    /** "Golems [Item] 2/3 (30)". */
    public Component golemLine(Player player) {
        if (!isEngineer(player)) {
            return null;
        }
        return ShopUi.text("§6Golems §7[Item] §f" + golems.copperCount(player.getUniqueId()) + "/" + settings.golemMax()
                + " §8(" + (int) settings.golemEnergyCost() + ")");
    }

    /** "G. de Ferro [Shift+F] ✔ (60)". */
    public Component ironGolemLine(Player player) {
        if (!isEngineer(player)) {
            return null;
        }
        return ShopUi.text("§fG. de Ferro §7[Shift+F] ")
                .append(mark(golems.canFuse(player.getUniqueId())))
                .append(ShopUi.text(" §8(" + (int) settings.ironGolemEnergyCost() + ")"));
    }

    /** "Reparar golem [Clique] (15)". */
    public Component golemRepairLine(Player player) {
        if (!isEngineer(player)) {
            return null;
        }
        return ShopUi.text("§eReparar golem §7[Clique] §8(" + (int) settings.golemRepairCost() + ")");
    }

    private static Component mark(boolean usable) {
        return usable ? Component.text("✔", NamedTextColor.GREEN) : Component.text("✖", NamedTextColor.RED);
    }
}
