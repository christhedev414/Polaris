package me.polarisclient.api.util.path.movement;

import me.polarisclient.api.util.path.world.BlockType;
import net.minecraft.util.math.BlockPos;

/**
 * Movement through water, including straight up and down.
 *
 * Water is the one medium where the player controls their vertical position directly: jump rises,
 * sneak sinks, and neither has a cooldown. That makes swimming a genuine 6-directional move rather
 * than the walk/jump/fall triple used on land.
 */
public class MovementSwim extends Movement {
   public MovementSwim(BlockPos from, BlockPos to, double cost) {
      super(from, to, cost);
   }

   @Override
   public MovementType getType() {
      return MovementType.SWIM;
   }

   @Override
   public void execute(MovementContext context) {
      double targetX = this.getTargetX();
      double targetZ = this.getTargetZ();
      int verticalDelta = this.to.getY() - this.from.getY();
      if (verticalDelta > 0) {
         context.jump();
      } else if (verticalDelta < 0) {
         context.setSneak(true);
      }

      // A purely vertical move has no heading, so steering would spin the player on the spot.
      if (this.to.getX() != this.from.getX() || this.to.getZ() != this.from.getZ()) {
         context.lookTowards(targetX, targetZ);
         context.moveTowards(targetX, targetZ);
      } else {
         context.stop();
      }
   }

   @Override
   public boolean isStillValid() {
      BlockType destination = typeAt(this.to);
      return destination == BlockType.WATER || canStandAt(this.to);
   }
}
