package me.polarisclient.api.util.path;

import net.minecraft.util.math.BlockPos;

public class GoalXZ implements Goal {
   private final int x;
   private final int z;

   public GoalXZ(int x, int z) {
      this.x = x;
      this.z = z;
   }

   @Override
   public boolean isFinished(BlockPos pos) {
      return pos.getX() == this.x && pos.getZ() == this.z;
   }

   @Override
   public double heuristic(BlockPos pos) {
      return PathFinder.octile(pos.getX() - this.x, pos.getZ() - this.z);
   }

   @Override
   public String toString() {
      return this.x + " " + this.z;
   }
}
