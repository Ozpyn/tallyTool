package dev.ozpyn.tallyTool;

import org.bukkit.plugin.java.JavaPlugin;
import dev.ozpyn.tallyTool.ItemTally;

import java.util.Objects;

public final class TallyTool extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        Objects.requireNonNull(getCommand("itemtally"), "Command 'itemtally' not registered in plugin.yml")
                .setExecutor(new ItemTally(this));
        getLogger().info("TallyTool started!");
    }

    @Override
    public void onDisable() {
        getLogger().info("TallyTool stopped!");
    }

}
