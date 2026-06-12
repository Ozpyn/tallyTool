package dev.ozpyn.tallyTool;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class EntityTally implements CommandExecutor {

    private final JavaPlugin plugin;

    public EntityTally(JavaPlugin plugin) {
        this.plugin = plugin;
    }

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

    private void tallyWithinRadius(Player player, int radius) {
        World world = player.getWorld();
        Location location = player.getLocation();
        boolean includePlayers = plugin.getConfig().getBoolean("entity-tally-include-players", false);

        Map<EntityType, Integer> counts = new HashMap<>();

        for (Entity entity : world.getNearbyEntities(location, radius, radius, radius)) {
            if (entity instanceof Player && !includePlayers) continue;
            counts.merge(entity.getType(), 1, Integer::sum);
        }

        sendOutput(player, counts);
    }

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

            Location center = new Location(world,
                    (minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
            double rx = (maxX - minX) / 2.0;
            double ry = (maxY - minY) / 2.0;
            double rz = (maxZ - minZ) / 2.0;

            boolean includePlayers = plugin.getConfig().getBoolean("entity-tally-include-players", false);
            Map<EntityType, Integer> counts = new HashMap<>();

            for (Entity entity : world.getNearbyEntities(center, rx, ry, rz)) {
                if (entity instanceof Player && !includePlayers) continue;
                counts.merge(entity.getType(), 1, Integer::sum);
            }

            sendOutput(player, counts);

        } catch (NumberFormatException ex) {
            player.sendMessage(Component.text("Coordinates must be numbers.").color(NamedTextColor.RED));
        }
    }

    private void sendOutput(Player player, Map<EntityType, Integer> counts) {
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();

        player.sendMessage(Component.text("=== Entity Tally ===").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("Total Entities: ").color(NamedTextColor.YELLOW)
                .append(Component.text(total).color(NamedTextColor.WHITE)));

        if (counts.isEmpty()) {
            player.sendMessage(Component.text("No entities found.").color(NamedTextColor.GRAY));
            return;
        }

        counts.entrySet().stream()
                .sorted(Map.Entry.<EntityType, Integer>comparingByValue(Comparator.reverseOrder()))
                .forEach(entry ->
                        player.sendMessage(
                                Component.text(entry.getKey().name()).color(NamedTextColor.GREEN)
                                        .append(Component.text(": ").color(NamedTextColor.WHITE))
                                        .append(Component.text(entry.getValue()).color(NamedTextColor.WHITE))
                        )
                );
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            int radius = plugin.getConfig().getInt("entity-tally-radius", 20);
            tallyWithinRadius(player, radius);
            return true;
        }
        if (args.length == 1) {
            try {
                int radius = Integer.parseInt(args[0]);
                if (radius < 0) {
                    player.sendMessage(Component.text("Radius must be positive.").color(NamedTextColor.RED));
                    return true;
                }
                tallyWithinRadius(player, radius);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Radius must be a number.").color(NamedTextColor.RED));
            }
            return true;
        }
        if (args.length == 6) {
            tallyRegion(player, args);
            return true;
        }
        player.sendMessage(Component.text("Usage: /entitytally [radius] OR /entitytally <x1 y1 z1 x2 y2 z2>").color(NamedTextColor.RED));
        return true;
    }
}
