package br.com.murilo.muriloplug.classes.engenheiro;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.structure.Structure;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Projetos das Plantas: plugins/MinePlug/plantas/&lt;uuid-da-planta&gt;/&lt;id-do-projeto&gt;.nbt, mais plantas.yml com
 * nome, tamanho, autor, data e quantidade de blocos de cada projeto.
 */
public final class PlantaStore {

    /** Um projeto guardado (os dados do plantas.yml). */
    public record Project(String id, String name, String size, String author, long date, int blocks) {
    }

    private final JavaPlugin plugin;
    private final File folder;
    private final File file;
    private YamlConfiguration yaml;

    public PlantaStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "plantas");
        this.file = new File(plugin.getDataFolder(), "plantas.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    private static String path(UUID planta, String projectId) {
        return "plantas." + planta + "." + projectId;
    }

    /** Arquivo .nbt do projeto, ou null se o id não for um UUID válido (evita caminhos estranhos). */
    public File file(UUID planta, String projectId) {
        try {
            return new File(new File(folder, planta.toString()), UUID.fromString(projectId) + ".nbt");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Projetos da Planta, do mais antigo para o mais novo. */
    public List<Project> list(UUID planta) {
        List<Project> result = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("plantas." + planta);
        if (section != null) {
            for (String id : section.getKeys(false)) {
                Project project = get(planta, id);
                if (project != null) {
                    result.add(project);
                }
            }
        }
        result.sort(Comparator.comparingLong(Project::date).thenComparing(Project::id));
        return result;
    }

    public int count(UUID planta) {
        return list(planta).size();
    }

    public Project get(UUID planta, String projectId) {
        ConfigurationSection section = yaml.getConfigurationSection(path(planta, projectId));
        File nbt = file(planta, projectId);
        if (section == null || nbt == null || !nbt.isFile()) {
            return null;
        }
        return new Project(projectId, section.getString("nome", "Projeto"), section.getString("tamanho", "?"),
                section.getString("autor", "?"), section.getLong("data"), section.getInt("blocos"));
    }

    /** Grava o arquivo e o registro. */
    public void add(UUID planta, Project project, Structure structure) throws IOException {
        File nbt = file(planta, project.id());
        if (nbt == null) {
            throw new IOException("id de projeto inválido: " + project.id());
        }
        nbt.getParentFile().mkdirs();
        Bukkit.getStructureManager().saveStructure(nbt, structure);
        String path = path(planta, project.id());
        yaml.set(path + ".nome", project.name());
        yaml.set(path + ".tamanho", project.size());
        yaml.set(path + ".autor", project.author());
        yaml.set(path + ".data", project.date());
        yaml.set(path + ".blocos", project.blocks());
        saveYaml();
    }

    public void rename(UUID planta, String projectId, String name) {
        if (yaml.contains(path(planta, projectId))) {
            yaml.set(path(planta, projectId) + ".nome", name);
            saveYaml();
        }
    }

    public void remove(UUID planta, String projectId) {
        File nbt = file(planta, projectId);
        if (nbt != null && nbt.isFile() && !nbt.delete()) {
            plugin.getLogger().warning("Não foi possível apagar " + nbt);
        }
        yaml.set(path(planta, projectId), null);
        ConfigurationSection left = yaml.getConfigurationSection("plantas." + planta);
        if (left != null && left.getKeys(false).isEmpty()) {
            yaml.set("plantas." + planta, null);
        }
        saveYaml();
    }

    /** Carrega a estrutura do projeto, ou null se o arquivo não existir. */
    public Structure load(UUID planta, String projectId) throws IOException {
        File nbt = file(planta, projectId);
        if (nbt == null || !nbt.isFile()) {
            return null;
        }
        return Bukkit.getStructureManager().loadStructure(nbt);
    }

    private void saveYaml() {
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar plantas.yml: " + e.getMessage());
        }
    }
}
