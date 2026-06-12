# tallyTool

A Paper plugin for scanning and tallying items and entities in Minecraft.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/itemtally` | Tally items in the container you're looking at | `tallytool.itemtally` |
| `/itemtally <x1> <y1> <z1> <x2> <y2> <z2>` | Tally all items in a cuboid region | `tallytool.itemtally` |
| `/entitytally [radius]` | Tally all entities within a radius | `tallytool.entitytally` |
| `/entitytally <x1> <y1> <z1> <x2> <y2> <z2>` | Tally all entities in a cuboid region | `tallytool.entitytally` |

All coordinates support `~` relative notation.

### itemtally

Scans containers (chests, barrels, hoppers, dispensers, etc.), entity inventories (minecarts with chests, llamas, etc.), item frames, and shulker boxes (with recursive nesting support). Ender chests are optional.

### entitytally

Scans all entities (mobs, animals, items, etc.) within a radius or cuboid region. Players are excluded by default.

## Configuration

```yaml
look-distance: 20
max-scan-volume: 100000
ignore-ender-chest: false
entity-tally-radius: 20
entity-tally-include-players: false
```

## Building

Requires JDK 26.

```sh
./gradlew build
```

The compiled JAR will be in `build/libs/`.
