package dev.ozpyn.tallyTool;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class ItemTally implements CommandExecutor {

    private final JavaPlugin plugin;

    public ItemTally(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------
    // DATA STRUCTURE
    // -----------------------------
    private static class TallyResult {
        Map<Material, Long> totals = new HashMap<>();
        Map<String, Map<Material, Long>> containerContents = new HashMap<>();
    }

    // -----------------------------
    // COORD PARSER (~ support)
    // -----------------------------
    private int parseCoordinate(String arg, double current) {
        if (arg.equals("~")) {
            return (int) Math.floor(current);
        }

        if (arg.startsWith("~")) {
            double offset = Double.parseDouble(arg.substring(1));
            return (int) Math.floor(current + offset);
        }

        return Integer.parseInt(arg);
    }

    // -----------------------------
    // RECURSIVE ITEM TALLY
    // -----------------------------
    private void tallyItem(ItemStack item, TallyResult result, String parent) {

        if (item == null) return;

        result.totals.merge(
                item.getType(),
                (long) item.getAmount(),
                Long::sum
        );

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockStateMeta)) return;

        if (!(blockStateMeta.getBlockState() instanceof ShulkerBox shulker)) return;

        String containerName = item.getType().name();

        Map<Material, Long> contents =
                result.containerContents.computeIfAbsent(containerName, k -> new HashMap<>());

        for (ItemStack nested : shulker.getInventory().getContents()) {

            if (nested == null) continue;

            contents.merge(
                    nested.getType(),
                    (long) nested.getAmount(),
                    Long::sum
            );

            tallyItem(nested, result, containerName);
        }
    }

    // -----------------------------
    // LOOK AT CONTAINER MODE
    // -----------------------------
    private boolean tallyLookedAtContainer(Player player) {

        int lookDistance = plugin.getConfig().getInt("look-distance", 20);

        Block target = player.getTargetBlockExact(lookDistance);

        if (target == null) {
            player.sendMessage("§cNo block in sight.");
            return true;
        }

        BlockState state = target.getState();

        if (!(state instanceof InventoryHolder holder)) {
            player.sendMessage("§cThat block is not a container.");
            return true;
        }

        Inventory inventory = holder.getInventory();

        TallyResult result = new TallyResult();

        for (ItemStack item : inventory.getContents()) {
            tallyItem(item, result, null);
        }

        sendOutput(player, result, 1);

        return true;
    }

    // -----------------------------
    // REGION MODE
    // -----------------------------
    private boolean tallyRegion(Player player, String[] args) {

        try {
            Location loc = player.getLocation();

            int x1 = parseCoordinate(args[0], loc.getX());
            int y1 = parseCoordinate(args[1], loc.getY());
            int z1 = parseCoordinate(args[2], loc.getZ());

            int x2 = parseCoordinate(args[3], loc.getX());
            int y2 = parseCoordinate(args[4], loc.getY());
            int z2 = parseCoordinate(args[5], loc.getZ());

            World world = player.getWorld();

            int minX = Math.min(x1, x2);
            int minY = Math.min(y1, y2);
            int minZ = Math.min(z1, z2);

            int maxX = Math.max(x1, x2);
            int maxY = Math.max(y1, y2);
            int maxZ = Math.max(z1, z2);

            TallyResult result = new TallyResult();

            int containers = 0;

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {

                        Block block = world.getBlockAt(x, y, z);
                        BlockState state = block.getState();

                        if (!(state instanceof InventoryHolder holder)) continue;

                        containers++;

                        for (ItemStack item : holder.getInventory().getContents()) {
                            tallyItem(item, result, null);
                        }
                    }
                }
            }

            player.sendMessage("§6=== Tally Results ===");
            player.sendMessage("§eContainers Scanned: §f" + containers);

            sendOutput(player, result, containers);

            return true;

        } catch (NumberFormatException ex) {
            player.sendMessage("§cCoordinates must be numbers.");
            return true;
        }
    }

    // -----------------------------
    // OUTPUT FORMAT
    // -----------------------------
    private void sendOutput(Player player, TallyResult result, int containers) {

        player.sendMessage("§6=== Container Tally ===");

        result.totals.entrySet().stream()
                .sorted(Map.Entry.<Material, Long>comparingByValue(Comparator.reverseOrder()))
                .forEach(entry ->
                        player.sendMessage(
                                "§a" + entry.getKey().name()
                                        + "§f: " + entry.getValue()
                        )
                );

        if (!result.containerContents.isEmpty()) {

            player.sendMessage("§6=== Nested Containers ===");

            result.containerContents.forEach((container, contents) -> {

                player.sendMessage("§b" + container);

                contents.entrySet().stream()
                        .sorted(Map.Entry.<Material, Long>comparingByValue(Comparator.reverseOrder()))
                        .forEach(entry ->
                                player.sendMessage(
                                        "§7- " + entry.getKey().name()
                                                + ": " + entry.getValue()
                                )
                        );
            });
        }
    }

    // -----------------------------
    // COMMAND ENTRY
    // -----------------------------
    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            return tallyLookedAtContainer(player);
        }

        if (args.length == 6) {
            return tallyRegion(player, args);
        }

        player.sendMessage("§cUsage: /itemtally OR /itemtally <x1 y1 z1 x2 y2 z2>");
        return true;
    }
}