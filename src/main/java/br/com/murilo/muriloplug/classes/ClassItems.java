package br.com.murilo.muriloplug.classes;

import br.com.murilo.muriloplug.classes.arqueiro.ArrowItems;
import br.com.murilo.muriloplug.classes.engenheiro.PlantaItems;
import br.com.murilo.muriloplug.classes.engenheiro.SentinelaItems;
import br.com.murilo.muriloplug.classes.engenheiro.StructureBooks;
import br.com.murilo.muriloplug.classes.mago.GrimoireItems;
import br.com.murilo.muriloplug.classes.mago.StaffItems;
import br.com.murilo.muriloplug.classes.mago.StaffType;
import br.com.murilo.muriloplug.classes.suporte.SuporteItem;
import br.com.murilo.muriloplug.classes.suporte.SuporteItems;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Registro central dos itens de classe: é por aqui que os comandos e o kit criam staffs, flechas e livros.
 * Cada classe nova com itens só precisa registrar os dela no construtor; eles entram sozinhos no kit.
 * Montar o item e reconhecê-lo (PersistentDataContainer) continua nas classes de cada item.
 */
public final class ClassItems {

    public static final String STAFF_PREFIX = "mago.staff_";
    public static final String GRIMOIRE = "mago.grimorio";
    public static final String EXPLOSIVE_ARROW = "arqueiro.flecha_explosiva";
    public static final String STRUCTURE_BOOK = "engenheiro.livro_estrutura";
    public static final String BLUEPRINT = "engenheiro.planta";
    public static final String GRAPPLE_ARROW = "engenheiro.flecha_gancho";
    public static final String SENTINEL = "engenheiro.sentinela";
    public static final String SUPPORT_LINK = "suporte.nucleo_vinculo";
    public static final String SUPPORT_ROOT = "suporte.estrela_prender";
    public static final String SUPPORT_HEAL = "suporte.coracao_cura";
    public static final String SUPPORT_SCROLL = "suporte.pergaminho_velocidade";
    public static final String SUPPORT_FLIGHT = "suporte.pena_voo";

    private static final int KIT_ARROWS = 16;
    private static final int KIT_BOOKS = 3;
    private static final int KIT_GRAPPLE_ARROWS = 16;
    private static final int KIT_SENTINELS = 2;

    /**
     * Um item registrado. No kit entram {@code kitCopies} itens criados com a quantidade {@code kitAmount} cada
     * (livros: 3 itens de 1, cada um com UUID próprio; flechas: 1 pilha de 16).
     */
    public record Entry(String id, PlayerClass owner, int kitCopies, int kitAmount, IntFunction<ItemStack> factory) {

        public ItemStack create(int amount) {
            return factory.apply(amount);
        }

        /** Quantidade total de unidades que o kit dá deste item. */
        public int kitTotal() {
            return kitCopies * kitAmount;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public ClassItems(StaffItems staffs, GrimoireItems grimoire, ArrowItems arrows, StructureBooks books,
                      PlantaItems blueprints, SentinelaItems sentinels,
                      SuporteItems support) {
        for (StaffType type : StaffType.values()) {
            register(new Entry(STAFF_PREFIX + type.id(), PlayerClass.MAGO, 1, 1, amount -> staffs.create(type)));
        }
        register(new Entry(GRIMOIRE, PlayerClass.MAGO, 1, 1, amount -> grimoire.create()));
        register(new Entry(EXPLOSIVE_ARROW, PlayerClass.ARQUEIRO, 1, KIT_ARROWS, arrows::createExplosive));
        register(new Entry(STRUCTURE_BOOK, PlayerClass.ENGENHEIRO, KIT_BOOKS, 1, amount -> books.createEmpty()));
        register(new Entry(BLUEPRINT, PlayerClass.ENGENHEIRO, 1, 1, amount -> blueprints.create()));
        register(new Entry(GRAPPLE_ARROW, PlayerClass.ENGENHEIRO, 1, KIT_GRAPPLE_ARROWS, arrows::createGrapple));
        register(new Entry(SENTINEL, PlayerClass.ENGENHEIRO, 1, KIT_SENTINELS, sentinels::create));
        registerSupport(SUPPORT_LINK, SuporteItem.VINCULO, support);
        registerSupport(SUPPORT_ROOT, SuporteItem.PRENDER, support);
        registerSupport(SUPPORT_HEAL, SuporteItem.CURA, support);
        registerSupport(SUPPORT_SCROLL, SuporteItem.PERGAMINHO, support);
        registerSupport(SUPPORT_FLIGHT, SuporteItem.VOO, support);
    }

    /** Item do Suporte: 1 de cada no kit (os de usos limitados saem cada um com o seu UUID). */
    private void registerSupport(String id, SuporteItem type, SuporteItems support) {
        register(new Entry(id, PlayerClass.SUPORTE, 1, 1, amount -> support.create(type)));
    }

    private void register(Entry entry) {
        entries.put(entry.id(), entry);
    }

    /** Cria o item registrado com este id (a quantidade vale para itens empilháveis), ou null se não existir. */
    public ItemStack create(String id, int amount) {
        Entry entry = entries.get(id);
        return entry == null ? null : entry.create(amount);
    }

    /** Itens do kit da classe, já criados (vazio se a classe não tem itens, como o Combatente). */
    public List<ItemStack> getItems(PlayerClass playerClass) {
        List<ItemStack> items = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.owner() == playerClass) {
                for (int i = 0; i < entry.kitCopies(); i++) {
                    items.add(entry.create(entry.kitAmount()));
                }
            }
        }
        return items;
    }

    /** Quantidade de itens do kit da classe (soma das unidades). */
    public int kitSize(PlayerClass playerClass) {
        return entries.values().stream().filter(entry -> entry.owner() == playerClass)
                .mapToInt(Entry::kitTotal).sum();
    }

    /** Classes que têm itens no registro, na ordem do menu /classe. */
    public List<PlayerClass> classesWithItems() {
        List<PlayerClass> result = new ArrayList<>();
        for (PlayerClass playerClass : PlayerClass.values()) {
            if (kitSize(playerClass) > 0) {
                result.add(playerClass);
            }
        }
        return result;
    }
}
