package com.maxxlive.jadepaperbridge.util;

import cn.lunadeer.dominion.api.dtos.flag.Flags;
import cn.lunadeer.dominion.api.dtos.flag.PriFlag;
import com.maxxlive.jadepaperbridge.accessor.Accessor;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.CopperChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShelfBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

public class DominionUtil {

    public static <A extends Accessor> boolean isRestricted(A accessor) {
        if (!(accessor.target() instanceof BlockEntity blockEntity) || !(accessor.target() instanceof Container container)) {
            return false;
        }
        var player = (org.bukkit.entity.Player) accessor.player().getBukkitEntity();
        if (player.getGameMode().isInvulnerable()) {
            return false;
        }
        var blockLoc = container.getLocation();
        if (blockLoc == null) {
            return false;
        }
        PriFlag priFlag;
        if (container instanceof ShulkerBoxBlockEntity) {
            priFlag = Flags.SHULKER_BOX;
        } else if (container instanceof ShelfBlockEntity) {
            priFlag = Flags.SHELF;
        } else if (blockEntity.getBlockState().getBlock() instanceof CopperChestBlock) {
            priFlag = Flags.COPPER_CHEST;
        } else {
            priFlag = switch (blockEntity.getBlockState().getBukkitMaterial()) {
                // containers and storage
                case CHEST, TRAPPED_CHEST -> Flags.CHEST;
                case BARREL -> Flags.BARREL;
                case HOPPER -> Flags.HOPPER;
                case DROPPER -> Flags.DROPPER;
                case DISPENSER -> Flags.DISPENSER;
                case FURNACE -> Flags.FURNACE;
                case BLAST_FURNACE -> Flags.BLAST_FURNACE;
                case SMOKER -> Flags.SMOKER;
                // crafting and utilities
                case JUKEBOX -> Flags.JUKEBOX;
                case LECTERN -> Flags.LECTERN;
                case CHISELED_BOOKSHELF -> Flags.BOOKSHELF;
                default -> null;
            };
        }
        return priFlag != null && !accessor.plugin().dominionApi().checkPrivilegeFlagSilence(blockLoc, priFlag, player);
    }

}
