package me.polarisclient.api.util.path;

import net.minecraft.util.math.BlockPos;

public class GoalNear implements Goal {
   private final BlockPos target;
   private final double radius;

   public GoalNear(BlockPos target, double radius) {
      this.target = target;
      this.radius = radius;
   }

   @Override
   public boolean isFinished(BlockPos pos) {
      return pos.distanceSq(this.target) <= this.radius * this.radius;
   }

   @Override
   public double heuristic(BlockPos pos) {
      double distance = PathFinder.octile(pos.getX() - this.target.getX(), pos.getZ() - this.target.getZ())
         + PathFinder.COST_VERTICAL * (double)Math.abs(pos.getY() - this.target.getY());
      return Math.max(0.0, distance - this.radius);
   }

   @Override
   public String toString() {
      return this.target.getX() + " " + this.target.getY() + " " + this.target.getZ() + " (r" + (int)this.radius + ")";
   }
}
