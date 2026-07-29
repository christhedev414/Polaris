package me.polarisclient.api.util.path;

import net.minecraft.util.math.BlockPos;

public class GoalBlock implements Goal {
   private final int x;
   private final int y;
   private final int z;

   public GoalBlock(int x, int y, int z) {
      this.x = x;
      this.y = y;
      this.z = z;
   }

   public GoalBlock(BlockPos pos) {
      this(pos.getX(), pos.getY(), pos.getZ());
   }

   public BlockPos getPos() {
      return new BlockPos(this.x, this.y, this.z);
   }

   @Override
   public boolean isFinished(BlockPos pos) {
      return pos.getX() == this.x && pos.getY() == this.y && pos.getZ() == this.z;
   }

   @Override
   public double heuristic(BlockPos pos) {
      return PathFinder.octile(pos.getX() - this.x, pos.getZ() - this.z) + PathFinder.COST_VERTICAL * (double)Math.abs(pos.getY() - this.y);
   }

   @Override
   public String toString() {
      return this.x + " " + this.y + " " + this.z;
   }
}
