package me.polarisclient.api.util.path.goal;

import net.minecraft.util.math.BlockPos;

/**
 * Reach this column at any height. This is the goal you want for long-distance travel, where
 * pinning an exact Y would make the search fight the terrain for no reason.
 */
public class GoalXZ implements Goal {
   private final int x;
   private final int z;

   public GoalXZ(int x, int z) {
      this.x = x;
      this.z = z;
   }

   @Override
   public boolean isFinished(int x, int y, int z) {
      return x == this.x && z == this.z;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      return Goal.octile(x - this.x, z - this.z);
   }

   @Override
   public BlockPos getRenderPos() {
      return new BlockPos(this.x, 0, this.z);
   }

   @Override
   public String toString() {
      return this.x + " " + this.z;
   }
}
