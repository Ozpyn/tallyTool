package dev.ozpyn.tallyTool;

import org.bukkit.plugin.java.JavaPlugin;
import dev.ozpyn.tallyTool.ItemTally;

public final class TallyTool extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        getCommand("itemtally").setExecutor(new ItemTally(this));
        getLogger().info("TallyTool started!");
    }

    @Override
    public void onDisable() {
        getLogger().info("TallyTool stopped!");
    }

}
