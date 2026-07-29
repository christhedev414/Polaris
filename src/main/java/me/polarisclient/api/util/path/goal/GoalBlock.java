package me.polarisclient.api.util.path.goal;

import net.minecraft.util.math.BlockPos;

/** Stand exactly here. */
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

   @Override
   public boolean isFinished(int x, int y, int z) {
      return x == this.x && y == this.y && z == this.z;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      // Vertical distance is counted at full weight: climbing or dropping a block still costs at
      // least as much as walking one, so this stays admissible.
      return Goal.octile(x - this.x, z - this.z) + (double)Math.abs(y - this.y);
   }

   @Override
   public BlockPos getRenderPos() {
      return new BlockPos(this.x, this.y, this.z);
   }

   @Override
   public String toString() {
      return this.x + " " + this.y + " " + this.z;
   }
}
