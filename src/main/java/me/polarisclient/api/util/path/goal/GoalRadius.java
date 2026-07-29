package me.polarisclient.api.util.path.goal;

import net.minecraft.util.math.BlockPos;

/**
 * Get within a sphere around a point. Gives the search room to stop somewhere sensible instead of
 * insisting on one exact block, which matters when the target block is itself unreachable - the
 * inside of a wall, the middle of a tree, the far side of a fence.
 */
public class GoalRadius implements Goal {
   private final int x;
   private final int y;
   private final int z;
   private final double radius;
   private final double radiusSq;

   public GoalRadius(BlockPos centre, double radius) {
      this(centre.getX(), centre.getY(), centre.getZ(), radius);
   }

   public GoalRadius(int x, int y, int z, double radius) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.radius = Math.max(0.0, radius);
      this.radiusSq = this.radius * this.radius;
   }

   @Override
   public boolean isFinished(int x, int y, int z) {
      double dx = (double)(x - this.x);
      double dy = (double)(y - this.y);
      double dz = (double)(z - this.z);
      return dx * dx + dy * dy + dz * dz <= this.radiusSq;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      double distance = Goal.octile(x - this.x, z - this.z) + (double)Math.abs(y - this.y);
      // Subtracting the radius keeps the estimate optimistic: never claim it is further than it is.
      return Math.max(0.0, distance - this.radius);
   }

   @Override
   public BlockPos getRenderPos() {
      return new BlockPos(this.x, this.y, this.z);
   }

   @Override
   public String toString() {
      return this.x + " " + this.y + " " + this.z + " r" + (int)this.radius;
   }
}
