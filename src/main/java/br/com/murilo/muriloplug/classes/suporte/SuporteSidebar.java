package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Linhas do sidebar do Suporte (null para quem não é Suporte): ✔ verde se der para usar (Harmonia suficiente e sem
 * recarga), ✖ vermelho se não, e o custo em cinza. Para uma habilidade nova, crie um método
 * {@code Component xxxLine(Player)} e coloque-o em {@link #lines}: o SidebarManager reserva uma linha para cada uma.
 *
 * <p>Para não passar de 15 linhas, Harmonia, Vínculo e Troca Equivalente aparecem sempre, e as habilidades só
 * aparecem para o item do Suporte que está na mão principal. Sem item do Suporte na mão, uma linha resume quantas
 * habilidades estão prontas.
 */
public final class SuporteSidebar {

    private final ClassManager classes;
    private final SuporteSettings settings;
    private final HarmoniaManager harmony;
    private final SuporteCooldowns cooldowns;
    private final LinkManager links;
    private final TrocaEquivalente swap;
    private final EloDrenante drain;
    private final SuporteItems items;
    private final TotemCura totems;
    /** Linhas na ordem de exibição. Uma linha que devolve null não aparece. */
    private final List<Function<Player, Component>> lines = List.of(this::harmonyLine, this::linkLine,
            this::swapLine, this::summaryLine, this::shieldLine, this::drainLine, this::placeLine, this::purifyLine,
            this::totemLine, this::silenceLine, this::curseLine);
    /** Habilidades com custo resumidas na linha "Habilidades: N/M prontas" (as que precisam de vínculo à parte). */
    private static final List<SuporteSkill> SUMMARY = List.of(SuporteSkill.ESCUDO, SuporteSkill.ELO,
            SuporteSkill.TROCA_LUGAR, SuporteSkill.PURIFICAR, SuporteSkill.SILENCIO, SuporteSkill.MALDICAO);

    public SuporteSidebar(ClassManager classes, SuporteSettings settings, HarmoniaManager harmony,
                          SuporteCooldowns cooldowns, LinkManager links, TrocaEquivalente swap, EloDrenante drain,
                          SuporteItems items, TotemCura totems) {
        this.items = items;
        this.totems = totems;
        this.classes = classes;
        this.settings = settings;
        this.harmony = harmony;
        this.cooldowns = cooldowns;
        this.links = links;
        this.swap = swap;
        this.drain = drain;
    }

    /** Quantas linhas o Suporte pode ocupar. */
    public int size() {
        return lines.size();
    }

    /** A linha de número {@code index}, ou null (não é Suporte ou a linha não se aplica agora). */
    public Component line(Player player, int index) {
        if (!classes.hasClass(player.getUniqueId(), PlayerClass.SUPORTE) || index < 0 || index >= lines.size()) {
            return null;
        }
        return lines.get(index).apply(player);
    }

    /** "Harmonia: 42/100". */
    private Component harmonyLine(Player player) {
        UUID id = player.getUniqueId();
        return ShopUi.text("§dHarmonia: §f" + (int) Math.floor(harmony.getHarmonia(id)) + "/"
                + (int) harmony.maxHarmonia());
    }

    /** "Vínculo [Núcleo] Fulano" (ou "nenhum"). */
    private Component linkLine(Player player) {
        LivingEntity ally = links.linkedAlly(player.getUniqueId());
        String name = ally == null ? "§8nenhum" : "§f" + (ally instanceof Player p ? p.getName() : "Boneco");
        return ShopUi.text("§5Vínculo §7[Núcleo] " + name);
    }

    /** "Troca Equiv. [Shift+Dir Núcleo] ON" (com a recarga, se houver). */
    private Component swapLine(Player player) {
        UUID id = player.getUniqueId();
        Component line = ShopUi.text("§dTroca Equiv. §7[Shift+Dir Núcleo] "
                + (swap.isEnabled(id) ? "§aON" : "§cOFF"));
        int wait = cooldowns.remainingSeconds(id, SuporteSkill.TROCA_EQUIVALENTE);
        return wait > 0 ? line.append(ShopUi.text(" §c" + wait + "s")) : line;
    }

    private SuporteItem held(Player player) {
        return items.read(player.getInventory().getItemInMainHand());
    }

    /** "Habilidades: 5/7 prontas" (só sem item do Suporte na mão). */
    private Component summaryLine(Player player) {
        if (held(player) != null) {
            return null;
        }
        int ready = 0;
        for (SuporteSkill skill : SUMMARY) {
            boolean needsLink = skill == SuporteSkill.ELO || skill == SuporteSkill.TROCA_LUGAR;
            if (usable(player, skill, needsLink)) {
                ready++;
            }
        }
        return ShopUi.text("§7Habilidades: §a" + ready + "§7/" + SUMMARY.size() + " prontas §8(segure um item)");
    }

    private Component shieldLine(Player player) {
        return held(player) != SuporteItem.ESCUDO ? null
                : skill(player, "§bEscudo §7[Dir Escudo] ", SuporteSkill.ESCUDO, false);
    }

    private Component drainLine(Player player) {
        if (held(player) != SuporteItem.VINCULO) {
            return null;
        }
        if (drain.isActive(player.getUniqueId())) {
            return ShopUi.text("§5Elo Drenante §7[Shift+Esq Núcleo] §5§lATIVO");
        }
        return skill(player, "§5Elo Drenante §7[Shift+Esq Núcleo] ", SuporteSkill.ELO, true);
    }

    private Component placeLine(Player player) {
        return held(player) != SuporteItem.VOO ? null
                : skill(player, "§7Troca Lugar §7[Shift+Esq Pena] ", SuporteSkill.TROCA_LUGAR, true);
    }

    private Component purifyLine(Player player) {
        return held(player) != SuporteItem.CURA ? null
                : skill(player, "§fPurificar §7[Shift+Esq Coração] ", SuporteSkill.PURIFICAR, false);
    }

    /** "Totem [Item] ativo" (ou "nenhum"); sempre aparece, é só uma linha. */
    private Component totemLine(Player player) {
        int active = totems.count(player.getUniqueId());
        int max = settings.totemMax();
        String state = active == 0 ? "§8nenhum" : max > 1 ? "§aativo (" + active + "/" + max + ")" : "§aativo";
        return ShopUi.text("§aTotem §7[Item] " + state);
    }

    private Component silenceLine(Player player) {
        return held(player) != SuporteItem.PRENDER ? null
                : skill(player, "§8Silêncio §7[Shift+Esq Estrela] ", SuporteSkill.SILENCIO, false);
    }

    private Component curseLine(Player player) {
        return held(player) != SuporteItem.PERGAMINHO ? null
                : skill(player, "§5Maldição §7[Shift+Esq Pergaminho] ", SuporteSkill.MALDICAO, false);
    }

    private boolean usable(Player player, SuporteSkill skill, boolean needsLink) {
        UUID id = player.getUniqueId();
        return cooldowns.remainingMillis(id, skill) <= 0 && harmony.temHarmonia(id, settings.cost(skill))
                && (!needsLink || links.linkedAlly(id) != null);
    }

    /** "Nome [Botão] ✔ (35)", com a recarga em vermelho quando houver. */
    private Component skill(Player player, String label, SuporteSkill skill, boolean needsLink) {
        double cost = settings.cost(skill);
        int wait = cooldowns.remainingSeconds(player.getUniqueId(), skill);
        Component line = ShopUi.text(label).append(mark(usable(player, skill, needsLink)))
                .append(ShopUi.text(" §8(" + (int) cost + ")"));
        return wait > 0 ? line.append(ShopUi.text(" §c" + wait + "s")) : line;
    }

    private static Component mark(boolean usable) {
        return usable ? Component.text("✔", NamedTextColor.GREEN) : Component.text("✖", NamedTextColor.RED);
    }
}
