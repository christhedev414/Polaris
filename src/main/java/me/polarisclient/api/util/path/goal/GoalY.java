package me.polarisclient.api.util.path.goal;

import net.minecraft.util.math.BlockPos;

/** Reach this Y level, wherever that happens to be. Useful for "get to the surface" or "dig to 11". */
public class GoalY implements Goal {
   private final int y;

   public GoalY(int y) {
      this.y = y;
   }

   @Override
   public boolean isFinished(int x, int y, int z) {
      return y == this.y;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      return (double)Math.abs(y - this.y);
   }

   @Override
   public BlockPos getRenderPos() {
      return new BlockPos(0, this.y, 0);
   }

   @Override
   public String toString() {
      return "y=" + this.y;
   }
}
