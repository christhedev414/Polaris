package me.polarisclient.api.util.path.movement;

import net.minecraft.util.math.BlockPos;

/**
 * Flat travel across one or more blocks, straight or diagonal.
 *
 * A walk may span several blocks after {@link me.polarisclient.api.util.path.PathOptimizer} merges a
 * collinear run, which is what lets long stretches be sprinted smoothly instead of the player
 * re-aiming at every block centre.
 */
public class MovementWalk extends Movement {
   /** Below this many blocks a sprint would be cancelled by the first corner, so do not bother. */
   private static final double SPRINT_DISTANCE = 2.0;

   public MovementWalk(BlockPos from, BlockPos to, double cost) {
      super(from, to, cost);
   }

   @Override
   public MovementType getType() {
      return MovementType.WALK;
   }

   @Override
   public void execute(MovementContext context) {
      double targetX = this.getTargetX();
      double targetZ = this.getTargetZ();
      context.lookTowards(targetX, targetZ);
      context.moveTowards(targetX, targetZ);
      if (this.spansEnoughToSprint()) {
         context.setSprint(true);
      }

      // The planner works in whole blocks and cannot see sub-block geometry: soul sand edges, carpet,
      // a fence post clipped into the corner. Hopping on horizontal collision clears those without
      // needing to model them, and costs nothing when the ground really is flat.
      if (context.getPlayer().collidedHorizontally && context.getPlayer().onGround) {
         context.jump();
      }
   }

   private boolean spansEnoughToSprint() {
      int dx = this.to.getX() - this.from.getX();
      int dz = this.to.getZ() - this.from.getZ();
      return Math.sqrt((double)(dx * dx + dz * dz)) >= SPRINT_DISTANCE || this.horizontalDistanceToTarget() >= SPRINT_DISTANCE;
   }
}
