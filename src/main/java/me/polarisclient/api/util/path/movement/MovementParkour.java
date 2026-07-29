package me.polarisclient.api.util.path.movement;

import net.minecraft.util.math.BlockPos;

/**
 * A running jump across a gap of two to four blocks.
 *
 * Off by default, and priced above walking, because it is the one movement that can kill you: a
 * missed jump over a ravine is unrecoverable in a way that a missed step never is. The planner only
 * emits these where the gap is genuinely empty - if there is any floor in between, walking is
 * cheaper and safer anyway.
 */
public class MovementParkour extends Movement {
   /**
    * How far past the centre of the starting block to jump. A sprint jump carries roughly four
    * blocks, but only if it leaves from the lip - jumping from the middle wastes half the distance.
    */
   private static final double JUMP_EDGE_DISTANCE = 0.32;

   public MovementParkour(BlockPos from, BlockPos to, double cost) {
      super(from, to, cost);
   }

   @Override
   public MovementType getType() {
      return MovementType.PARKOUR;
   }

   /** Gap length in blocks. */
   public int getDistance() {
      return Math.abs(this.to.getX() - this.from.getX()) + Math.abs(this.to.getZ() - this.from.getZ());
   }

   @Override
   public void execute(MovementContext context) {
      double targetX = this.getTargetX();
      double targetZ = this.getTargetZ();
      context.lookTowards(targetX, targetZ);
      context.moveTowards(targetX, targetZ);
      // Sprint is not optional here. A walking jump clears two blocks at most, and the planner has
      // already committed to a gap that may be four.
      context.setSprint(true);
      if (context.getPlayer().onGround && this.horizontalDistanceFromStart() >= JUMP_EDGE_DISTANCE) {
         context.jump();
      }
   }

   @Override
   public boolean isStillValid() {
      if (!canStandAt(this.to)) {
         return false;
      } else {
         // Re-check the flight path: a block placed into the gap turns a clean jump into a faceplant.
         int steps = this.getDistance();
         int stepX = Integer.signum(this.to.getX() - this.from.getX());
         int stepZ = Integer.signum(this.to.getZ() - this.from.getZ());

         for(int i = 1; i < steps; ++i) {
            BlockPos intermediate = new BlockPos(this.from.getX() + stepX * i, this.from.getY(), this.from.getZ() + stepZ * i);
            if (!hasClearance(intermediate)) {
               return false;
            }
         }

         return true;
      }
   }
}
