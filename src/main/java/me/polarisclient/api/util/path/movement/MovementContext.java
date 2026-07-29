package me.polarisclient.api.util.path.movement;

import me.polarisclient.api.util.Wrapper;
import me.polarisclient.api.util.path.RotationController;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;

/**
 * Everything a {@link Movement} is allowed to do to the player for one tick.
 *
 * Movements never touch the player directly. Funnelling every control through one object means the
 * executor decides what a movement may do - it can cap sprinting, suppress rotation, or drop all
 * input during recovery - without any movement needing to know about it.
 *
 * Lives for a single tick and is thrown away.
 */
public class MovementContext implements Wrapper {
   private final EntityPlayerSP player;
   private final MovementInput input;
   private final RotationController rotation;
   private final boolean sprintAllowed;

   public MovementContext(EntityPlayerSP player, MovementInput input, RotationController rotation, boolean sprintAllowed) {
      this.player = player;
      this.input = input;
      this.rotation = rotation;
      this.sprintAllowed = sprintAllowed;
   }

   public EntityPlayerSP getPlayer() {
      return this.player;
   }

   public MovementInput getInput() {
      return this.input;
   }

   public boolean isSprintAllowed() {
      return this.sprintAllowed;
   }

   /**
    * Walks toward a world position.
    *
    * The desired heading is decomposed into forward/strafe components relative to the player's
    * CURRENT yaw, rather than forcing the camera to face the target. That is what lets rotation be
    * optional: whatever direction the player happens to be looking, the resulting motion vector is
    * the same. It also means the server sees ordinary WASD movement.
    */
   public void moveTowards(double x, double z) {
      double dx = x - this.player.posX;
      double dz = z - this.player.posZ;
      if (dx * dx + dz * dz >= 1.0E-6) {
         double desiredYaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
         double delta = Math.toRadians(desiredYaw - (double)this.player.rotationYaw);
         // Positive strafe is left in Minecraft, hence the negated sine.
         this.input.moveForward = (float)Math.cos(delta);
         this.input.moveStrafe = (float)(-Math.sin(delta));
      }
   }

   /** Points the view at a world position, subject to the controller's mode. */
   public void lookTowards(double x, double z) {
      this.rotation.lookTowards(x, z);
   }

   public void jump() {
      this.input.jump = true;
   }

   public void setSneak(boolean sneak) {
      this.input.sneak = sneak;
   }

   /**
    * Sprinting is only ever turned ON here. Clearing it is left to vanilla, which already stops
    * sprinting on collision, hunger and item use - fighting that produces visible stutter.
    */
   public void setSprint(boolean sprint) {
      if (sprint && this.sprintAllowed) {
         this.player.setSprinting(true);
      }
   }

   /** Drops all movement input for this tick. */
   public void stop() {
      this.input.moveForward = 0.0F;
      this.input.moveStrafe = 0.0F;
   }
}
