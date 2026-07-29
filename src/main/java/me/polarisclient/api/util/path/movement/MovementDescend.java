package me.polarisclient.api.util.path.movement;

import net.minecraft.util.math.BlockPos;

/** Drop down one or more blocks, walking off the edge. */
public class MovementDescend extends Movement {
   /** Beyond this drop, sprinting overshoots the landing block often enough to be worth avoiding. */
   private static final int SPRINT_SAFE_DROP = 1;

   public MovementDescend(BlockPos from, BlockPos to, double cost) {
      super(from, to, cost);
   }

   @Override
   public MovementType getType() {
      return MovementType.DESCEND;
   }

   public int getDrop() {
      return this.from.getY() - this.to.getY();
   }

   @Override
   public void execute(MovementContext context) {
      double targetX = this.getTargetX();
      double targetZ = this.getTargetZ();
      context.lookTowards(targetX, targetZ);
      context.moveTowards(targetX, targetZ);
      if (this.getDrop() <= SPRINT_SAFE_DROP) {
         context.setSprint(true);
      }
   }

   @Override
   public boolean isStillValid() {
      if (!canStandAt(this.to)) {
         return false;
      } else {
         // Nothing may have appeared in the shaft we are about to fall through.
         for(int y = this.from.getY(); y > this.to.getY(); --y) {
            if (!typeAt(new BlockPos(this.to.getX(), y, this.to.getZ())).isPassable()) {
               return false;
            }
         }

         return true;
      }
   }
}
