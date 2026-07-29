package me.polarisclient.api.util.path.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockVine;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * The pathfinder's view of a block. Every block in the world collapses to one of these categories,
 * which is what lets {@link BlockCache} store a whole chunk as a flat byte array and lets the
 * planning thread reason about terrain without ever touching a {@link net.minecraft.world.World}.
 *
 * The ordinals are persisted into those byte arrays, so the declaration order is load-bearing:
 * adding a constant is fine, reordering them is not.
 */
public enum BlockType {
   /** Not cached yet. Treated as impassable so we never plan through terrain we have not seen. */
   UNKNOWN,
   /** Empty, or a block with no collision box (torches, tall grass, signs). */
   AIR,
   /** Has a collision box. Blocks movement, and can be stood on top of. */
   SOLID,
   /** Swimmable. Breaks falls from any height and can be moved through vertically. */
   WATER,
   /** Instant death. Never entered. */
   LAVA,
   /** Hurts on contact - fire, cactus, magma. Never entered. */
   DANGER,
   /** Passable but punishing, such as cobwebs. Entered only when the cost is worth it. */
   AVOID,
   /** Ladders and vines. Passable, and supports the player vertically. */
   CLIMBABLE;

   private static final BlockType[] VALUES = values();

   public static BlockType byOrdinal(int ordinal) {
      return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : UNKNOWN;
   }

   /** Whether the player's body can occupy this block. */
   public boolean isPassable() {
      return this == AIR || this == WATER || this == AVOID || this == CLIMBABLE;
   }

   /** Whether this block supports the player standing on top of it. */
   public boolean isStandable() {
      return this == SOLID;
   }

   /** Whether this block holds the player up from the inside, rather than from below. */
   public boolean isSupporting() {
      return this == WATER || this == CLIMBABLE;
   }

   public boolean isLiquid() {
      return this == WATER || this == LAVA;
   }

   /**
    * Classifies a block state. Must be called from the main thread, because collision boxes are
    * resolved against the live world - some blocks (fences, doors, gates) shape themselves from
    * their neighbours.
    */
   public static BlockType classify(IBlockState state, IBlockAccess world, BlockPos pos) {
      Block block = state.getBlock();
      if (block instanceof BlockAir) {
         return AIR;
      }

      Material material = state.getMaterial();
      if (material == Material.LAVA) {
         return LAVA;
      }

      if (material == Material.WATER) {
         return WATER;
      }

      if (block instanceof BlockLadder || block instanceof BlockVine) {
         return CLIMBABLE;
      }

      if (block == Blocks.FIRE || block instanceof BlockCactus || block == Blocks.MAGMA) {
         return DANGER;
      }

      if (block == Blocks.WEB) {
         return AVOID;
      }

      // No collision box means the player walks straight through it: torches, grass, rails, signs.
      return state.getCollisionBoundingBox(world, pos) == null ? AIR : SOLID;
   }
}
