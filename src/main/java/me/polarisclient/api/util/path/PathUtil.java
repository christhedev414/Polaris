package me.polarisclient.api.util.path;

import me.polarisclient.api.util.Wrapper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;

public class PathUtil implements Wrapper {
   private PathUtil() {
   }

   public static boolean isLoaded(BlockPos pos) {
      return mc.world != null && mc.world.isBlockLoaded(pos);
   }

   public static boolean isDangerous(BlockPos pos) {
      IBlockState state = mc.world.getBlockState(pos);
      Block block = state.getBlock();
      return state.getMaterial() == Material.LAVA
         || block == Blocks.FIRE
         || block == Blocks.CACTUS
         || block == Blocks.MAGMA;
   }

   public static boolean isWater(BlockPos pos) {
      return mc.world.getBlockState(pos).getMaterial() == Material.WATER;
   }

   /**
    * Whether the player's body can occupy this block without being stopped or hurt by it.
    */
   public static boolean isPassable(BlockPos pos) {
      if (!isLoaded(pos)) {
         return false;
      } else {
         IBlockState state = mc.world.getBlockState(pos);
         Block block = state.getBlock();
         if (block instanceof BlockAir) {
            return true;
         } else if (isDangerous(pos) || block == Blocks.WEB) {
            return false;
         } else {
            return state.getCollisionBoundingBox(mc.world, pos) == null;
         }
      }
   }

   /**
    * Whether this block can be stood on top of.
    */
   public static boolean isStandable(BlockPos pos) {
      if (!isLoaded(pos)) {
         return false;
      } else {
         IBlockState state = mc.world.getBlockState(pos);
         if (state.getBlock() instanceof BlockAir || state.getMaterial().isLiquid() || isDangerous(pos)) {
            return false;
         } else {
            return state.getCollisionBoundingBox(mc.world, pos) != null;
         }
      }
   }

   /**
    * Whether the player can hold this position with their feet in it. Standing on a solid block
    * counts, and so does floating in water.
    */
   public static boolean canStandAt(BlockPos pos) {
      return hasClearance(pos) && (isStandable(pos.down()) || isWater(pos));
   }

   /**
    * Whether the player's two-block-tall body fits at this position, ignoring what is underneath.
    */
   public static boolean hasClearance(BlockPos pos) {
      return isPassable(pos) && isPassable(pos.up());
   }

   /**
    * Given a feet position the player would fall from, returns the position they would land on,
    * or null if the drop is unsurvivable or obstructed. Water breaks the fall at any height.
    */
   public static BlockPos fallTarget(BlockPos pos, int maxFall) {
      for(int drop = 1; drop <= maxFall; ++drop) {
         BlockPos candidate = pos.down(drop);
         if (!isLoaded(candidate)) {
            return null;
         }

         if (isWater(candidate)) {
            return candidate;
         }

         if (!isPassable(candidate)) {
            return null;
         }

         if (isStandable(candidate.down())) {
            return candidate;
         }
      }

      return null;
   }
}
