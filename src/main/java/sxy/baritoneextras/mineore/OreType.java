package sxy.baritoneextras.mineore;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

public enum OreType {
    COAL("coal", Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
    IRON("iron", Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
    COPPER("copper", Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
    GOLD("gold", Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE),
    REDSTONE("redstone", Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
    LAPIS("lapis", Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
    DIAMOND("diamond", Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
    EMERALD("emerald", Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
    QUARTZ("quartz", Blocks.NETHER_QUARTZ_ORE),
    ANCIENT_DEBRIS("ancient_debris", Blocks.ANCIENT_DEBRIS);

    public final String configKey;
    private final Block[] blocks;

    private static final Map<Block, OreType> LOOKUP = new HashMap<>();

    static {
        for (OreType type : values()) {
            for (Block block : type.blocks) {
                LOOKUP.put(block, type);
            }
        }
    }

    OreType(String configKey, Block... blocks) {
        this.configKey = configKey;
        this.blocks = blocks;
    }

    public static OreType classify(Block block) {
        return LOOKUP.get(block);
    }
}
