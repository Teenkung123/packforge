package dev.packbench.observer;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Creates only a new benchmark world; never opens or changes existing saves. */
final class BenchmarkScene {
    static final String WORLD_ID = SceneIdentity.WORLD_ID;
    static final long SEED = SceneIdentity.SEED;
    private static final BlockPos SIGN = new BlockPos(0, 66, 0);
    private final long deadline = System.nanoTime() + 180_000_000_000L;
    private volatile Throwable failure;
    private volatile boolean populated;
    private boolean populationScheduled;
    private long warmupStart;
    private int warmupFrames;
    private List<String> observedEntityUuids = List.of();

    BenchmarkScene(Minecraft minecraft) {
        SceneIdentity.validateExpected();
        Path game = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        Path output = Path.of(System.getProperty("packforge.benchmark.output")).toAbsolutePath().normalize();
        if (!output.getParent().equals(game)) {
            throw new IllegalArgumentException("scene output must be inside its isolated game directory");
        }
        Path saves = game.resolve("saves");
        Path world = saves.resolve(WORLD_ID);
        if (Files.isSymbolicLink(game) || Files.isSymbolicLink(saves) || Files.exists(world)) {
            throw new IllegalArgumentException("benchmark scene requires a new world and no symbolic-link save directory");
        }
    }

    void create(Minecraft minecraft) {
        try {
            LevelSettings settings = new LevelSettings("Packbench deterministic scene v1", GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true,
                WorldDataConfiguration.DEFAULT);
            minecraft.createWorldOpenFlows().createFreshLevel(WORLD_ID, settings,
                new WorldOptions(SEED, false, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT)
                    .value().createWorldDimensions(), ClientReadiness.titleScreen(minecraft));
        } catch (RuntimeException | Error error) {
            failure = error;
        }
    }

    boolean poll(Minecraft minecraft) {
        if (failure != null) throw new IllegalStateException("deterministic scene failed", failure);
        if (System.nanoTime() > deadline) throw new IllegalStateException("deterministic scene readiness timed out");
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) return false;
        if (!populationScheduled) {
            populationScheduled = true;
            var server = minecraft.getSingleplayerServer();
            server.execute(() -> {
                try {
                    ServerLevel level = server.overworld();
                    server.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
                    server.getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
                    server.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
                    server.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, server);
                    SceneCommandOutput output = new SceneCommandOutput();
                    CommandSourceStack source = server.createCommandSourceStack().withSource(output);
                    String[] commands = {
                        "time set noon", "weather clear",
                        "fill -12 63 -12 12 64 12 minecraft:stone",
                        "fill -6 65 -3 -3 68 -3 minecraft:glass",
                        "fill 3 65 -3 6 68 -3 minecraft:glass_pane",
                        "fill -5 64 0 -4 64 1 minecraft:water",
                        "fill 4 64 0 5 64 1 minecraft:lava",
                        "setblock -2 65 -1 minecraft:campfire",
                        "setblock 0 65 0 minecraft:stone",
                        "setblock 0 66 0 minecraft:oak_sign[rotation=0]",
                        "summon minecraft:armor_stand 2.5 65 -0.5 {UUID:" + SceneIdentity.nbtUuid(SceneIdentity.ARMOR_STAND)
                            + ",NoGravity:1b,Invulnerable:1b,Motion:[0d,0d,0d],Rotation:[180f,0f],Tags:[\"packbench_scene\"]}",
                        "item replace entity @e[type=minecraft:armor_stand,tag=packbench_scene,limit=1] armor.head with minecraft:diamond_helmet",
                        "item replace entity @e[type=minecraft:armor_stand,tag=packbench_scene,limit=1] weapon.mainhand with minecraft:diamond_sword",
                        "summon minecraft:pig -2.5 65 -1.5 {UUID:" + SceneIdentity.nbtUuid(SceneIdentity.PIG)
                            + ",NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Motion:[0d,0d,0d],Rotation:[180f,0f],Tags:[\"packbench_scene\"]}",
                        "summon minecraft:item 1.5 66 0.5 {UUID:" + SceneIdentity.nbtUuid(SceneIdentity.ITEM)
                            + ",Item:{id:\"minecraft:clock\",count:1},NoGravity:1b,Invulnerable:1b,Age:-32768s,Motion:[0d,0d,0d],Rotation:[180f,0f],Tags:[\"packbench_scene\"]}",
                        "item replace entity @a weapon.mainhand with minecraft:diamond_sword",
                        "tp @a 0.5 65 7.5 180 8"
                    };
                    for (String command : commands) {
                        server.getCommands().performPrefixedCommand(source, command);
                        if (output.failure != null) throw new IllegalStateException(command + ": " + output.failure);
                    }
                    if (!(level.getBlockEntity(SIGN) instanceof SignBlockEntity sign)) {
                        throw new IllegalStateException("benchmark sign was not created");
                    }
                    sign.setText(sign.getFrontText().setMessage(0, Component.literal("Packbench v1"))
                        .setMessage(1, Component.literal("Aa 0123 Å Ω Ж"))
                        .setMessage(2, Component.literal("ก ข ค 中 文"))
                        .setMessage(3, Component.literal("Fallback · space")), true);
                    sign.setChanged();
                    level.sendBlockUpdated(SIGN, sign.getBlockState(), sign.getBlockState(), 3);
                    populated = true;
                } catch (RuntimeException | Error error) {
                    failure = error;
                }
            });
            return false;
        }
        if (!populated || !ClientReadiness.worldReady(minecraft)) return false;
        if (!verifyCurrent(minecraft)) return false;
        if (warmupStart == 0) warmupStart = System.nanoTime();
        ++warmupFrames;
        return warmupFrames >= 120 && System.nanoTime() - warmupStart >= 15_000_000_000L;
    }

    boolean verifyCurrent(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null
            || !minecraft.level.getBlockState(SIGN).is(Blocks.OAK_SIGN)
            || !(minecraft.player.distanceToSqr(0.5, 65, 7.5) <= 1.0)
            || !(Math.abs(Math.IEEEremainder(minecraft.player.getYRot() - 180.0, 360.0)) <= 0.1)
            || !(Math.abs(minecraft.player.getXRot() - 8.0) <= 0.1)) return false;
        Set<UUID> sceneEntities = new HashSet<>();
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (SceneIdentity.ENTITY_UUIDS.contains(entity.getUUID()) && entity.distanceToSqr(0.5, 65, 0.5) < 100) {
                sceneEntities.add(entity.getUUID());
            }
        }
        observedEntityUuids = SceneIdentity.ENTITY_UUIDS.stream().filter(sceneEntities::contains).map(UUID::toString).toList();
        return sceneEntities.containsAll(SceneIdentity.ENTITY_UUIDS);
    }

    List<String> observedEntityUuids() { return observedEntityUuids; }

    private static final class SceneCommandOutput implements CommandSource {
        private String failure;
        @Override public void sendSystemMessage(Component message) { failure = message.getString(); }
        @Override public boolean acceptsSuccess() { return false; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }
}
