package me.polarisclient.api.util.path.movement;

import me.polarisclient.api.util.path.world.BlockType;
import net.minecraft.util.math.BlockPos;

/**
 * Movement up or down a ladder or vine.
 *
 * Climbing in Minecraft is asymmetric, which is why this is not just a vertical walk. Going up
 * needs input - holding into the ladder, or jump. Going down needs the ABSENCE of input: the player
 * slides down at a fixed rate as long as they stay inside the climbable block, and pressing forward
 * would pin them in place instead.
 */
public class MovementClimb extends Movement {
   public MovementClimb(BlockPos from, BlockPos to, double cost) {
      super(from, to, cost);
   }

   @Override
   public MovementType getType() {
      return MovementType.CLIMB;
   }

   @Override
   public void execute(MovementContext context) {
      if (this.to.getY() > this.from.getY()) {
         // Jump climbs reliably on both ladders and vines; pressing into the block alone can stall
         // on vines, which have no collision face to push against.
         context.jump();
         context.moveTowards(this.getTargetX(), this.getTargetZ());
      } else {
         // Let go and sink. Any forward input here would hold the player still.
         context.stop();
      }
   }

   @Override
   public boolean isStillValid() {
      return typeAt(this.to) == BlockType.CLIMBABLE || canStandAt(this.to);
   }
}
