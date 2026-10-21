package br.com.murilo.muriloplug.classes.engenheiro;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.structure.Structure;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Arquivos das estruturas salvas: plugins/MinePlug/estruturas/&lt;id&gt;.nbt (o id é um UUID). */
public final class StructureStore {

    private final File folder;

    public StructureStore(JavaPlugin plugin) {
        this.folder = new File(plugin.getDataFolder(), "estruturas");
    }

    /** Arquivo da estrutura, ou null se o id não for um UUID válido (evita caminhos estranhos). */
    public File file(String id) {
        try {
            return new File(folder, UUID.fromString(id) + ".nbt");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Quantos arquivos de estrutura existem na pasta. */
    public int count() {
        String[] names = folder.list((dir, name) -> name.endsWith(".nbt"));
        return names == null ? 0 : names.length;
    }

    public void save(String id, Structure structure) throws IOException {
        File file = file(id);
        if (file == null) {
            throw new IOException("id de estrutura inválido: " + id);
        }
        folder.mkdirs();
        Bukkit.getStructureManager().saveStructure(file, structure);
    }

    /** Carrega a estrutura salva (para a parte 2: colocar no mundo), ou null se o arquivo não existir. */
    public Structure load(String id) throws IOException {
        File file = file(id);
        if (file == null || !file.isFile()) {
            return null;
        }
        return Bukkit.getStructureManager().loadStructure(file);
    }
}
