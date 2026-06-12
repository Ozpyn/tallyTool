package dev.ozpyn.tallyTool;

import org.bukkit.plugin.java.JavaPlugin;
import dev.ozpyn.tallyTool.ItemTally;
import dev.ozpyn.tallyTool.EntityTally;

import java.util.Objects;

public final class TallyTool extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Objects.requireNonNull(getCommand("itemtally"), "Command 'itemtally' not registered in plugin.yml")
                .setExecutor(new ItemTally(this));
        Objects.requireNonNull(getCommand("entitytally"), "Command 'entitytally' not registered in plugin.yml")
                .setExecutor(new EntityTally(this));
        getLogger().info("TallyTool started!");
    }

    @Override
    public void onDisable() {
        getLogger().info("TallyTool stopped!");
    }

}
