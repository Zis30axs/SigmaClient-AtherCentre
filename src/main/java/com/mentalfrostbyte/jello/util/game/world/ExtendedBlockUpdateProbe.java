package com.mentalfrostbyte.jello.util.game.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Receive-side probe for re-injected block updates.
 *
 * <p>{@link ExtendedHeightBlockUpdateHandler} can prove the injected bytes are
 * well formed and that they reach the vanilla decoder, but not that the world
 * actually ends up holding the block. This logs what the world contains
 * immediately after {@code invalidateRegionAndSetBlock} so the failure can be
 * split into "the write did not happen" versus "the write happened but nothing
 * re-rendered".
 *
 * <p>Diagnostic only. Gated by the same
 * {@code -Dsigma.viamcp.debugBlockUpdateReinject=true} switch as the send side
 * and limited to Y outside the legacy 0..255 range so normal play is silent.
 */
public final class ExtendedBlockUpdateProbe {
    private static final Logger LOGGER = LogManager.getLogger("ExtendedHeightBlockUpdates");

    private ExtendedBlockUpdateProbe() {
    }

    public static void afterBlockChange(World world, BlockPos pos, BlockState expected) {
        if (!ExtendedHeightBlockUpdateHandler.isDebugEnabled()
                || WorldHeightHelper.isTranslatedBlockYInBounds(pos.getY())) {
            return;
        }

        try {
            BlockState actual = world.getBlockState(pos);
            Chunk chunk = world.getChunkAt(pos);
            ChunkSection[] sections = chunk.getSections();
            int sectionIndex = WorldHeightHelper.blockYToSectionIndex(pos.getY());
            ChunkSection section = sectionIndex >= 0 && sectionIndex < sections.length
                    ? sections[sectionIndex]
                    : null;

            LOGGER.info(
                    "[ExtendedHeightProbe] APPLIED pos={} expected={}({}) actual={}({}) match={} "
                            + "outsideBuildHeight={} chunk={} chunkEmpty={} sections={} sectionIndex={} "
                            + "sectionPresent={} sectionEmpty={} extended={} minY={} maxY={}",
                    pos, describe(expected), Block.getStateId(expected), describe(actual),
                    Block.getStateId(actual), actual == expected,
                    World.isOutsideBuildHeight(pos), chunk.getClass().getSimpleName(), chunk.isEmpty(),
                    sections.length, sectionIndex, section != Chunk.EMPTY_SECTION,
                    section == Chunk.EMPTY_SECTION || section.isEmpty(),
                    WorldHeightHelper.isExtendedHeight(), WorldHeightHelper.getMinY(),
                    WorldHeightHelper.getMaxY());
        } catch (Throwable t) {
            LOGGER.warn("[ExtendedHeightProbe] APPLIED probe failed at {}: {}", pos, t.toString());
        }
    }

    private static String describe(BlockState state) {
        if (state == null) {
            return "null";
        }

        return String.valueOf(Registry.BLOCK.getKey(state.getBlock()));
    }
}
