package me.polarisclient.api.util.path.movement;

import net.minecraft.util.math.BlockPos;

/** Step or jump up exactly one block. */
public class MovementAscend extends Movement {
   /**
    * How close to the target column the player must be before jumping. Jumping too early lands
    * short and leaves the player stuck against the face of the block; too late and the forward
    * momentum is gone. Just over one block is the sweet spot for vanilla jump physics.
    */
   private static final double JUMP_RANGE = 1.25;

   public MovementAscend(BlockPos from, BlockPos to, double cost) {
      super(from, to, cost);
   }

   @Override
   public MovementType getType() {
      return MovementType.ASCEND;
   }

   @Override
   public void execute(MovementContext context) {
      double targetX = this.getTargetX();
      double targetZ = this.getTargetZ();
      context.lookTowards(targetX, targetZ);
      context.moveTowards(targetX, targetZ);
      if (context.getPlayer().onGround && this.horizontalDistanceToTarget() < JUMP_RANGE) {
         context.jump();
      }
   }

   @Override
   public boolean isStillValid() {
      // The block being stepped onto must still be there, and the ceiling above the starting
      // position must still be open or the jump has nowhere to go.
      return canStandAt(this.to) && typeAt(this.from.up(2)).isPassable();
   }
}
