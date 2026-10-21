package br.com.murilo.muriloplug.classes;

import br.com.murilo.muriloplug.classes.arqueiro.ArrowItems;
import br.com.murilo.muriloplug.classes.engenheiro.StructureBooks;
import br.com.murilo.muriloplug.classes.mago.StaffItems;
import br.com.murilo.muriloplug.classes.mago.StaffType;
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
    public static final String EXPLOSIVE_ARROW = "arqueiro.flecha_explosiva";
    public static final String STRUCTURE_BOOK = "engenheiro.livro_estrutura";

    private static final int KIT_ARROWS = 16;
    private static final int KIT_BOOKS = 3;

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

    public ClassItems(StaffItems staffs, ArrowItems arrows, StructureBooks books) {
        for (StaffType type : StaffType.values()) {
            register(new Entry(STAFF_PREFIX + type.id(), PlayerClass.MAGO, 1, 1, amount -> staffs.create(type)));
        }
        register(new Entry(EXPLOSIVE_ARROW, PlayerClass.ARQUEIRO, 1, KIT_ARROWS, arrows::createExplosive));
        register(new Entry(STRUCTURE_BOOK, PlayerClass.ENGENHEIRO, KIT_BOOKS, 1, amount -> books.createEmpty()));
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
