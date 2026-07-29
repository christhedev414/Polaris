package me.polarisclient.api.util.combat;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.World;

/**
 * An immutable cube of "can I place here" / "is this solid" flags around a point.
 *
 * Exists so placement searches can run off the tick thread. Minecraft's World is not safe to read
 * concurrently - the network thread mutates chunks as block updates arrive, so an off-thread
 * getBlockState can observe a half-applied change or trip a chunk load - and a placement search is
 * exactly the kind of work you want off the tick thread.
 *
 * The lifecycle is: build on the MAIN thread, then never touch the array again. Publishing a fully
 * built object through a final field makes every read from the worker safe without locking. A
 * snapshot is single-use and thrown away after the search it was captured for.
 *
 * Positions outside the captured cube report as neither replaceable nor solid, so a search can only
 * ever be too conservative at the boundary, never wrong.
 */
public final class TerrainSnapshot {
   private static final byte REPLACEABLE = 1;
   private static final byte SOLID = 2;

   private final int originX;
   private final int originY;
   private final int originZ;
   private final int size;
   private final byte[] flags;

   private TerrainSnapshot(int originX, int originY, int originZ, int size, byte[] flags) {
      this.originX = originX;
      this.originY = originY;
      this.originZ = originZ;
      this.size = size;
      this.flags = flags;
   }

   /**
    * Captures a cube of edge {@code 2 * radius + 1} centred on a block. MAIN THREAD ONLY.
    *
    * Both queries are resolved here rather than lazily, because both need the live world: whether a
    * block can be replaced, and whether it has a collision box to stand a bed on.
    */
   public static TerrainSnapshot capture(World world, BlockPos centre, int radius) {
      int size = radius * 2 + 1;
      byte[] flags = new byte[size * size * size];
      int originX = centre.getX() - radius;
      int originY = centre.getY() - radius;
      int originZ = centre.getZ() - radius;
      MutableBlockPos cursor = new MutableBlockPos();

      for(int dx = 0; dx < size; ++dx) {
         for(int dy = 0; dy < size; ++dy) {
            for(int dz = 0; dz < size; ++dz) {
               int x = originX + dx;
               int y = originY + dy;
               int z = originZ + dz;
               if (y >= 0 && y < 256) {
                  cursor.setPos(x, y, z);
                  IBlockState state = world.getBlockState(cursor);
                  byte value = 0;
                  if (state.getBlock() == Blocks.AIR || state.getBlock().isReplaceable(world, cursor)) {
                     value = (byte)(value | REPLACEABLE);
                  }

                  if (state.isFullBlock() || state.getMaterial().isSolid() && state.getCollisionBoundingBox(world, cursor) != null) {
                     value = (byte)(value | SOLID);
                  }

                  flags[(dx * size + dy) * size + dz] = value;
               }
            }
         }
      }

      return new TerrainSnapshot(originX, originY, originZ, size, flags);
   }

   public boolean contains(int x, int y, int z) {
      int dx = x - this.originX;
      int dy = y - this.originY;
      int dz = z - this.originZ;
      return dx >= 0 && dx < this.size && dy >= 0 && dy < this.size && dz >= 0 && dz < this.size;
   }

   /** A block a bed or support could be placed into. Outside the cube reads false. */
   public boolean isReplaceable(int x, int y, int z) {
      return (this.get(x, y, z) & REPLACEABLE) != 0;
   }

   /** A block with a collision box, i.e. something a bed can rest on. Outside the cube reads false. */
   public boolean isSolid(int x, int y, int z) {
      return (this.get(x, y, z) & SOLID) != 0;
   }

   public boolean isReplaceable(BlockPos pos) {
      return this.isReplaceable(pos.getX(), pos.getY(), pos.getZ());
   }

   public boolean isSolid(BlockPos pos) {
      return this.isSolid(pos.getX(), pos.getY(), pos.getZ());
   }

   private byte get(int x, int y, int z) {
      int dx = x - this.originX;
      int dy = y - this.originY;
      int dz = z - this.originZ;
      if (dx >= 0 && dx < this.size && dy >= 0 && dy < this.size && dz >= 0 && dz < this.size) {
         return this.flags[(dx * this.size + dy) * this.size + dz];
      } else {
         return 0;
      }
   }
}
