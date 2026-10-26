package br.com.murilo.muriloplug.classes;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registro dos itens especiais do plugin que NÃO são de classe (Núcleo, Cofre e Baú do Clan, Contratos, dinheiro...).
 * O /kit admin mostra todos pelo botão "Itens do Servidor". Item novo: só registrar aqui (no onEnable).
 *
 * <p>Grupos grandes (upgrades e máquinas) entram como categoria: um ícone que abre um submenu com uma linha por
 * tipo. As linhas são montadas na hora de abrir, então acompanham mudanças na config sem reiniciar.
 */
public final class ServerItems {

    /** Uma categoria: nome e ícone do botão, e as linhas de itens (cada fábrica cria um item novo a cada chamada). */
    public record Category(String id, String name, Material icon, Supplier<List<List<Supplier<ItemStack>>>> rows) {
    }

    private final Map<String, Supplier<ItemStack>> entries = new LinkedHashMap<>();
    private final Map<String, Category> categories = new LinkedHashMap<>();

    /** Registra um item (cada chamada da fábrica cria um item novo). */
    public void register(String id, Supplier<ItemStack> factory) {
        entries.put(id, factory);
    }

    /** Registra uma categoria (submenu com uma linha por tipo). */
    public void registerCategory(String id, String name, Material icon, Supplier<List<List<Supplier<ItemStack>>>> rows) {
        categories.put(id, new Category(id, name, icon, rows));
    }

    public int size() {
        return entries.size();
    }

    /** 1 de cada item registrado (sem as categorias), na ordem do registro. */
    public List<ItemStack> all() {
        List<ItemStack> items = new ArrayList<>();
        entries.values().forEach(factory -> items.add(factory.get()));
        return items;
    }

    /** Fábricas dos itens avulsos, na ordem do registro. */
    public List<Supplier<ItemStack>> factories() {
        return List.copyOf(entries.values());
    }

    public List<Category> categories() {
        return List.copyOf(categories.values());
    }
}
