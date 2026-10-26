package br.com.murilo.muriloplug.classes;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registro dos itens especiais do plugin que NÃO são de classe (Núcleo, Cofre e Baú do Clan, Contratos, dinheiro...).
 * O /kit admin entrega 1 de cada pelo botão "Itens do Servidor". Item novo: só registrar aqui (no onEnable).
 */
public final class ServerItems {

    private final Map<String, Supplier<ItemStack>> entries = new LinkedHashMap<>();

    /** Registra um item (cada chamada da fábrica cria um item novo). */
    public void register(String id, Supplier<ItemStack> factory) {
        entries.put(id, factory);
    }

    public int size() {
        return entries.size();
    }

    /** 1 de cada item registrado, na ordem do registro. */
    public List<ItemStack> all() {
        List<ItemStack> items = new ArrayList<>();
        entries.values().forEach(factory -> items.add(factory.get()));
        return items;
    }
}
