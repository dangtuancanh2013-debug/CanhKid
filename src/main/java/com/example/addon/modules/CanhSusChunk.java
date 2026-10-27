package com.example.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.Map;

public class CanhSusChunk extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final IntSetting simDistance = sg.add(new IntSetting.Builder()
        .name("simulation-distance").description("Chunk radius to scan.")
        .defaultValue(4).min(1).sliderRange(1, 16).onChanged(v -> results.clear()).build());

    private final IntSetting sensitivity = sg.add(new IntSetting.Builder()
        .name("sensitivity").description("Suspicious blocks needed to flag a chunk (lower = more sensitive).")
        .defaultValue(4).min(1).sliderRange(1, 20).build());

    private final IntSetting alpha = sg.add(new IntSetting.Builder()
        .name("alpha").description("Highlight transparency.")
        .defaultValue(71).range(0, 255).sliderRange(0, 255).build());

    private final BoolSetting kelp = bool("kelp", true);
    private final BoolSetting caveVines = bool("cave-vines", true);
    private final BoolSetting vines = bool("vines", false);
    private final BoolSetting amethyst = bool("amethyst", true);
    private final BoolSetting bamboo = bool("bamboo", false);
    private final BoolSetting beeNest = bool("bee-nest", true);
    private final BoolSetting rotatedDeepslate = bool("rotated-deepslate", true);

    private final Map<ChunkPos, Integer> results = new HashMap<>();

    public CanhSusChunk() {
        super(Categories.World, "canh-sus-chunk", "Highlights chunks with signs of player activity.");
    }

    private BoolSetting bool(String name, boolean def) {
        return sg.add(new BoolSetting.Builder().name(name).description("Detect " + name + ".")
            .defaultValue(def).onChanged(v -> results.clear()).build());
    }

    @Override
    public void onDeactivate() {
        results.clear();
    }

    // ---- scanning ----
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        ChunkPos center = mc.player.getChunkPos();
        int r = simDistance.get();
        results.keySet().removeIf(p -> Math.max(Math.abs(p.x - center.x), Math.abs(p.z - center.z)) > r + 2);

        int scanned = 0;
        for (int dx = -r; dx <= r && scanned < 2; dx++) {
            for (int dz = -r; dz <= r && scanned < 2; dz++) {
                ChunkPos p = new ChunkPos(center.x + dx, center.z + dz);
                if (results.containsKey(p)) continue;
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(p.x, p.z);
                if (chunk == null) continue;
                results.put(p, scan(chunk));
                scanned++;
            }
        }
    }

    private boolean interesting(BlockState s) {
        Block b = s.getBlock();
        return (kelp.get() && (b == Blocks.KELP || b == Blocks.KELP_PLANT))
            || (caveVines.get() && (b == Blocks.CAVE_VINES || b == Blocks.CAVE_VINES_PLANT))
            || (vines.get() && b == Blocks.VINE)
            || (amethyst.get() && (b == Blocks.AMETHYST_CLUSTER || b == Blocks.SMALL_AMETHYST_BUD
                || b == Blocks.MEDIUM_AMETHYST_BUD || b == Blocks.LARGE_AMETHYST_BUD))
            || (bamboo.get() && (b == Blocks.BAMBOO || b == Blocks.BAMBOO_SAPLING))
            || (beeNest.get() && b == Blocks.BEE_NEST)
            || (rotatedDeepslate.get() && b == Blocks.DEEPSLATE);
    }

    private int scan(WorldChunk chunk) {
        int count = 0;
        ChunkSection[] sections = chunk.getSectionArray();
        int sx = chunk.getPos().getStartX(), sz = chunk.getPos().getStartZ();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty() || !sec.hasAny(this::interesting)) continue;
            int baseY = chunk.getBottomY() + i * 16;
            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 16; y++)
                    for (int z = 0; z < 16; z++) {
                        BlockState s = sec.getBlockState(x, y, z);
                        if (!interesting(s)) continue;
                        pos.set(sx + x, baseY + y, sz + z);
                        if (suspicious(mc.world, s, pos)) count++;
                    }
        }
        return count;
    }

    private boolean suspicious(World world, BlockState s, BlockPos pos) {
        Block b = s.getBlock();
        RegistryEntry<Biome> biome = world.getBiome(pos);

        if (b == Blocks.KELP || b == Blocks.KELP_PLANT)
            return !biome.isIn(BiomeTags.IS_OCEAN);

        if (b == Blocks.CAVE_VINES || b == Blocks.CAVE_VINES_PLANT)
            return !biome.matchesKey(BiomeKeys.LUSH_CAVES);

        if (b == Blocks.VINE)
            return !(biome.isIn(BiomeTags.IS_JUNGLE) || biome.matchesKey(BiomeKeys.SWAMP)
                || biome.matchesKey(BiomeKeys.MANGROVE_SWAMP));

        if (b == Blocks.AMETHYST_CLUSTER || b == Blocks.SMALL_AMETHYST_BUD
            || b == Blocks.MEDIUM_AMETHYST_BUD || b == Blocks.LARGE_AMETHYST_BUD) {
            Direction facing = s.get(Properties.FACING);
            Block support = world.getBlockState(pos.offset(facing.getOpposite())).getBlock();
            return support != Blocks.AMETHYST_BLOCK && support != Blocks.BUDDING_AMETHYST;
        }

        if (b == Blocks.BAMBOO || b == Blocks.BAMBOO_SAPLING)
            return !biome.isIn(BiomeTags.IS_JUNGLE);

        if (b == Blocks.BEE_NEST)
            return !(biome.matchesKey(BiomeKeys.PLAINS) || biome.matchesKey(BiomeKeys.SUNFLOWER_PLAINS)
                || biome.matchesKey(BiomeKeys.MEADOW) || biome.matchesKey(BiomeKeys.FOREST)
                || biome.matchesKey(BiomeKeys.FLOWER_FOREST) || biome.matchesKey(BiomeKeys.BIRCH_FOREST)
                || biome.matchesKey(BiomeKeys.OLD_GROWTH_BIRCH_FOREST) || biome.matchesKey(BiomeKeys.CHERRY_GROVE));

        if (b == Blocks.DEEPSLATE)
            return s.get(Properties.AXIS) != Direction.Axis.Y;

        return false;
    }

    // ---- rendering ----
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || results.isEmpty()) return;

        Color side = new Color(255, 25, 25, alpha.get());
        Color line = new Color(255, 25, 25, Math.min(255, alpha.get() + 100));
        double y1 = mc.player.getY() - 3, y2 = y1 + 0.2;

        for (Map.Entry<ChunkPos, Integer> e : results.entrySet()) {
            if (e.getValue() < sensitivity.get()) continue;
            ChunkPos p = e.getKey();
            event.renderer.box(p.getStartX(), y1, p.getStartZ(), p.getStartX() + 16, y2, p.getStartZ() + 16,
                side, line, ShapeMode.Both, 0);
        }
    }
}
