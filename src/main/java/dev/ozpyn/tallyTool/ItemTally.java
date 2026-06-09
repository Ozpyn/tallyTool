package dev.ozpyn.tallyTool;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.EnderChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.NonNull;

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
    private void tallyLookedAtContainer(Player player) {

        int lookDistance = plugin.getConfig().getInt("look-distance", 20);

        Block target = player.getTargetBlockExact(lookDistance);

        if (target == null) {
            player.sendMessage(Component.text("No block in sight.").color(NamedTextColor.RED));
            return;
        }

        BlockState state = target.getState();

        if (state instanceof InventoryHolder holder) {
            Inventory inventory = holder.getInventory();

            TallyResult result = new TallyResult();

            for (ItemStack item : inventory.getContents()) {
                tallyItem(item, result, null);
            }

            sendOutput(player, result, 1);
            return;
        }

        if (state instanceof EnderChest) {
            if (plugin.getConfig().getBoolean("ignore-ender-chest", true)){
                player.sendMessage(Component.text("Ender Chest scanning is disabled.").color(NamedTextColor.RED));
                return;
            }
            Inventory inventory = player.getEnderChest();

            TallyResult result = new TallyResult();

            for (ItemStack item : inventory.getContents()) {
                tallyItem(item, result, null);
            }

            sendOutput(player, result, 1);
            return;
        }

        player.sendMessage(Component.text("That block is not a container.").color(NamedTextColor.RED));
    }

    // -----------------------------
    // REGION MODE
    // -----------------------------
    private void tallyRegion(Player player, String[] args) {

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

            int volume = (maxX - minX) * (maxY - minY) * (maxZ - minZ);

            if(volume > plugin.getConfig().getInt("max-scan-volume", 100000)) {
                player.sendMessage("The attempted action involves " + volume + " blocks, but the max is " + plugin.getConfig().getInt("max-scan-volume", 100000) + ".");
                return;
            }

            TallyResult result = new TallyResult();

            int containers = 0;
            boolean enderChestTallied = plugin.getConfig().getBoolean("ignore-ender-chest", true);

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {

                        Block block = world.getBlockAt(x, y, z);
                        BlockState state = block.getState();

                        if (state instanceof InventoryHolder holder) {
                            containers++;

                            for (ItemStack item : holder.getInventory().getContents()) {
                                tallyItem(item, result, null);
                            }
                            continue;
                        }

                        if (state instanceof EnderChest && !enderChestTallied) {
                            containers++;
                            enderChestTallied = true;

                            for (ItemStack item : player.getEnderChest().getContents()) {
                                tallyItem(item, result, null);
                            }
                        }
                    }
                }
            }

            Location center = new Location(world,
                    (minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
            double rx = (maxX - minX) / 2.0;
            double ry = (maxY - minY) / 2.0;
            double rz = (maxZ - minZ) / 2.0;

            for (Entity entity : world.getNearbyEntities(center, rx, ry, rz)) {
                if (entity instanceof InventoryHolder holder) {
                    containers++;

                    for (ItemStack item : holder.getInventory().getContents()) {
                        tallyItem(item, result, null);
                    }
                    continue;
                }

                if (entity instanceof ItemFrame frame) {
                    ItemStack item = frame.getItem();
                    if (item != null && !item.getType().isAir()) {
                        containers++;
                        tallyItem(item, result, null);
                    }
                }
            }

            player.sendMessage(Component.text("=== Tally Results ===").color(NamedTextColor.GOLD));
            player.sendMessage(Component.text("Containers Scanned: ").color(NamedTextColor.YELLOW)
                            .append(Component.text(containers).color(NamedTextColor.WHITE)));

            sendOutput(player, result, containers);

        } catch (NumberFormatException ex) {
            player.sendMessage(Component.text("Coordinates must be numbers.").color(NamedTextColor.RED));
        }
    }

    // -----------------------------
    // OUTPUT FORMAT
    // -----------------------------
    private void sendOutput(Player player, TallyResult result, int containers) {

        player.sendMessage(Component.text("=== Container Tally ===").color(NamedTextColor.GOLD));

        result.totals.entrySet().stream()
                .sorted(Map.Entry.<Material, Long>comparingByValue(Comparator.reverseOrder()))
                .forEach(entry ->
                        player.sendMessage(
                                Component.text(entry.getKey().name()).color(NamedTextColor.GREEN)
                                        .append(Component.text(": ").color(NamedTextColor.WHITE))
                                        .append(Component.text(entry.getValue()).color(NamedTextColor.WHITE))
                        )
                );

        if (!result.containerContents.isEmpty()) {

            player.sendMessage(Component.text("=== Nested Containers ===").color(NamedTextColor.GOLD));

            result.containerContents.forEach((container, contents) -> {

                player.sendMessage(Component.text(container).color(NamedTextColor.AQUA));

                contents.entrySet().stream()
                        .sorted(Map.Entry.<Material, Long>comparingByValue(Comparator.reverseOrder()))
                        .forEach(entry ->
                                player.sendMessage(
                                        Component.text("- ").color(NamedTextColor.GRAY)
                                                .append(Component.text(entry.getKey().name()).color(NamedTextColor.GRAY))
                                                .append(Component.text(": ").color(NamedTextColor.GRAY))
                                                .append(Component.text(entry.getValue()).color(NamedTextColor.GRAY))
                                )
                        );
            });
        }
    }

    // -----------------------------
    // COMMAND ENTRY
    // -----------------------------
    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            tallyLookedAtContainer(player);
            return true;
        }

        if (args.length == 6) {
            tallyRegion(player, args);
            return true;
        }

        player.sendMessage(Component.text("Usage: /itemtally OR /itemtally <x1 y1 z1 x2 y2 z2>").color(NamedTextColor.RED));
        return true;
    }
}