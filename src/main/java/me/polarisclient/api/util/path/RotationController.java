package me.polarisclient.api.util.path;

import me.polarisclient.api.util.Wrapper;
import net.minecraft.util.math.MathHelper;

/**
 * Turns the player's view toward where the path is heading.
 *
 * Rotation is deliberately optional. The movement code steers by decomposing the desired direction
 * into forward/strafe input RELATIVE to wherever the player is currently facing, so it walks the
 * path correctly even with rotation switched off entirely - the camera simply stays under the
 * user's control. Rotation is therefore about looking natural, not about being able to move.
 *
 * {@link Mode#SNAP} is the one to avoid on servers that care: instantaneous view changes are the
 * single most obvious tell in a rotation packet stream.
 */
public class RotationController implements Wrapper {
   private RotationController.Mode mode = RotationController.Mode.SMOOTH;
   private float speed = 18.0F;
   private boolean hasTarget;
   private float targetYaw;

   public void setMode(RotationController.Mode mode) {
      this.mode = mode;
   }

   public RotationController.Mode getMode() {
      return this.mode;
   }

   /** Maximum degrees of yaw change per tick in {@link Mode#SMOOTH}. */
   public void setSpeed(float speed) {
      this.speed = Math.max(1.0F, speed);
   }

   /** Aims at a world position. Call every tick while a movement is running. */
   public void lookTowards(double x, double z) {
      if (!nullCheck()) {
         double dx = x - mc.player.posX;
         double dz = z - mc.player.posZ;
         this.targetYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
         this.hasTarget = true;
      }
   }

   public void clearTarget() {
      this.hasTarget = false;
   }

   /**
    * Applies the pending rotation. Called once per tick by the executor, AFTER movements have had a
    * chance to set their target, so a single tick never applies two conflicting turns.
    */
   public void update() {
      if (this.hasTarget && this.mode != RotationController.Mode.OFF && !nullCheck()) {
         float current = mc.player.rotationYaw;
         float difference = MathHelper.wrapDegrees(this.targetYaw - current);
         if (this.mode == RotationController.Mode.SNAP) {
            mc.player.rotationYaw = this.targetYaw;
         } else {
            // Clamping the per-tick delta is what makes the turn read as a person rather than a bot.
            mc.player.rotationYaw = current + MathHelper.clamp(difference, -this.speed, this.speed);
         }
      }
   }

   private static boolean nullCheck() {
      return mc.player == null || mc.world == null;
   }

   public enum Mode {
      /** Never touch the camera. The player still follows the path. */
      OFF,
      /** Ease toward the path direction at a capped rate. */
      SMOOTH,
      /** Face the path direction immediately. */
      SNAP;
   }
}
