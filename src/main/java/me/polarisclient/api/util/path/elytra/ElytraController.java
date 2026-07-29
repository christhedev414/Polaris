package me.polarisclient.api.util.path.elytra;

import me.polarisclient.api.util.InventoryUtil;
import me.polarisclient.api.util.Timer;
import me.polarisclient.api.util.Wrapper;
import me.polarisclient.api.util.path.goal.Goal;
import me.polarisclient.api.util.path.world.BlockCache;
import me.polarisclient.api.util.path.world.BlockType;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemElytra;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.util.EnumHand;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/**
 * Flies the player to a goal on an elytra.
 *
 * Deliberately NOT a movement type in the block A*. Air is everywhere, so an aerial node graph is
 * effectively unbounded - a 200 block flight spans millions of candidate positions with almost no
 * structure to prune against, and the resulting "path" would be a straight line through empty space
 * that took a hundred milliseconds to discover. Flight is a control problem, not a search problem,
 * so this is a closed-loop controller that shares the goal system with the ground pathfinder but
 * plans nothing.
 *
 * The physics worth knowing, because the whole design follows from it: the elytra is an unpowered
 * glider. Pitching down converts altitude into speed, pitching up converts speed into altitude, and
 * there is no thrust except fireworks. A steep climb with no speed behind it does not climb - it
 * stalls and drops. So climb angle is capped by current speed rather than being a free parameter,
 * and the controller spends altitude to buy speed whenever it has altitude to spare.
 *
 * Unlike ground movement, this MUST control the camera: the elytra derives its entire flight vector
 * from where the player is looking, so there is no equivalent of the yaw-relative input trick that
 * lets walking leave the view alone.
 */
public class ElytraController implements Wrapper {
   /** Deploy attempts before giving up, in case the server refuses the fall-flying start. */
   private static final int MAX_LAUNCH_ATTEMPTS = 20;
   /** Altitude error, in blocks, tolerated before the controller bothers correcting. */
   private static final double ALTITUDE_DEADBAND = 3.0;
   /** How hard to pull up when terrain is detected on the flight path. */
   private static final double TERRAIN_AVOID_CLIMB = 15.0;

   private final BlockCache cache;
   private final Timer boostTimer = new Timer();
   private ElytraSettings settings = ElytraSettings.defaults();
   private ElytraState state = ElytraState.GROUNDED;
   private Goal goal;
   private String status = "Idle";
   private int launchAttempts;

   public ElytraController(BlockCache cache) {
      this.cache = cache;
   }

   public void setSettings(ElytraSettings settings) {
      this.settings = settings;
   }

   public void setGoal(Goal goal) {
      this.goal = goal;
      this.reset();
   }

   public void reset() {
      this.state = ElytraState.GROUNDED;
      this.launchAttempts = 0;
      this.status = "Idle";
   }

   public ElytraState getState() {
      return this.state;
   }

   public String getStatus() {
      return this.status;
   }

   /** Whether the player is wearing an elytra with durability left. */
   public static boolean hasUsableElytra() {
      if (mc.player == null) {
         return false;
      } else {
         ItemStack chest = mc.player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
         return chest.getItem() == Items.ELYTRA && ItemElytra.isUsable(chest);
      }
   }

   public void tick(MovementInput input) {
      if (mc.player != null && mc.world != null && this.goal != null) {
         // Contact with the ground ends elytra flight in vanilla, so it ends the flight here too.
         if (this.state.isFlying() && mc.player.onGround) {
            this.state = ElytraState.LANDED;
            this.status = "Landed";
         } else if (!this.state.isTerminal()) {
            if (!hasUsableElytra()) {
               this.state = ElytraState.FAILED;
               this.status = "No usable elytra";
            } else if (!mc.player.isElytraFlying()) {
               this.launch(input);
            } else {
               this.fly();
            }
         }
      }
   }

   /**
    * Gets airborne and opens the wings.
    *
    * Fall-flying can only start while off the ground, so this hops first and deploys on the next
    * tick. The deploy is a packet rather than a simulated jump press because the vanilla client only
    * checks for the key on a rising edge, which is awkward to fake reliably from an input hook.
    */
   private void launch(MovementInput input) {
      if (mc.player.onGround) {
         this.state = ElytraState.GROUNDED;
         this.status = "Taking off";
         input.jump = true;
      } else {
         mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_FALL_FLYING));
         this.state = ElytraState.TAKEOFF;
         if (++this.launchAttempts > MAX_LAUNCH_ATTEMPTS) {
            this.state = ElytraState.FAILED;
            this.status = "Could not take off";
         }
      }
   }

   /** One tick of closed-loop flight: aim, choose a target altitude, pitch toward it, boost if slow. */
   private void fly() {
      BlockPos target = this.goal.getRenderPos();
      double dx = (double)target.getX() + 0.5 - mc.player.posX;
      double dz = (double)target.getZ() + 0.5 - mc.player.posZ;
      double distance = Math.sqrt(dx * dx + dz * dz);
      double speed = this.horizontalSpeed();
      double groundLevel = this.surfaceHeightAt(MathHelper.floor(mc.player.posX), MathHelper.floor(mc.player.posZ));
      double aboveGround = mc.player.posY - groundLevel;
      this.aimYaw(dx, dz);

      double desiredY;
      if (distance <= (double)this.settings.getLandingDistance()) {
         if (aboveGround <= (double)this.settings.getFlareHeight()) {
            // Flare: pull the nose up to trade forward speed for a survivable touchdown. Elytra
            // landing damage scales with impact speed, so this is what makes arrival non-lethal.
            this.state = ElytraState.FLARE;
            desiredY = mc.player.posY + 6.0;
         } else {
            this.state = ElytraState.DESCEND;
            desiredY = this.goal.constrainsAltitude() ? (double)target.getY() : groundLevel;
         }
      } else {
         desiredY = this.cruiseAltitude(dx, dz, distance, groundLevel);
         this.state = mc.player.posY < desiredY - ALTITUDE_DEADBAND ? ElytraState.CLIMB : ElytraState.CRUISE;
      }

      // Terrain avoidance overrides everything, including a landing approach: a hill between here
      // and the target has to be cleared before any of the rest matters.
      if (this.isTerrainAhead()) {
         this.state = ElytraState.CLIMB;
         desiredY = Math.max(desiredY, mc.player.posY + TERRAIN_AVOID_CLIMB);
      }

      this.applyPitch(desiredY, speed);
      if (this.settings.isUseFireworks() && this.state != ElytraState.FLARE && this.state != ElytraState.DESCEND) {
         this.maybeBoost(speed);
      }

      this.status = this.state + " " + (int)distance + "m " + (int)(speed * 100.0) + "cm/t";
   }

   /**
    * Pitches toward a target altitude, with the climb angle limited by current speed.
    *
    * The speed clamp is the important half. Commanding 30 degrees of climb at low speed does not
    * climb; the glider stalls, drops, and ends up lower than it started, which without this cap
    * turns into a sawtooth oscillation that slowly flies the player into the ground.
    */
   private void applyPitch(double desiredY, double speed) {
      double error = desiredY - mc.player.posY;
      // Negative pitch is upward in Minecraft, hence the sign flip.
      float pitch = (float)(-error * (double)this.settings.getPitchGain());
      float maxClimb = speed < this.settings.getStallSpeed() ? 5.0F : this.settings.getMaxClimbPitch();
      this.aimPitch(MathHelper.clamp(pitch, -maxClimb, this.settings.getMaxDivePitch()));
   }

   /**
    * Cruise altitude: clearance above the highest terrain on the way to the target.
    *
    * Columns the terrain cache has not captured yet are ignored rather than treated as ground level.
    * Flight easily outruns the cache, and reading an uncaptured column as "terrain at y=0" would
    * command a dive into whatever is actually there. Unknown terrain is instead left to the live
    * raycast in {@link #isTerrainAhead()}, which is never stale.
    */
   private double cruiseAltitude(double dx, double dz, double distance, double groundLevel) {
      double stepX = dx / distance;
      double stepZ = dz / distance;
      double highest = groundLevel;
      int samples = 6;
      int spacing = Math.max(4, this.settings.getLookAhead() / samples);

      for(int i = 1; i <= samples; ++i) {
         int probe = i * spacing;
         int x = MathHelper.floor(mc.player.posX + stepX * (double)probe);
         int z = MathHelper.floor(mc.player.posZ + stepZ * (double)probe);
         double surface = this.surfaceHeightAt(x, z);
         if (surface >= 0.0) {
            highest = Math.max(highest, surface);
         }
      }

      double ceiling = (double)(BlockCache.WORLD_HEIGHT - 8);
      return Math.min(ceiling, highest + (double)this.settings.getCruiseHeight());
   }

   /**
    * Highest solid block in a column, or -1 if the cache has not seen it.
    *
    * Scans downward from a little above the player rather than from the world ceiling, since
    * anything above the player is irrelevant to how high the ground is.
    */
   private double surfaceHeightAt(int x, int z) {
      int top = Math.min(BlockCache.WORLD_HEIGHT - 1, MathHelper.floor(mc.player.posY) + 8);
      boolean known = false;

      for(int y = top; y > 0; --y) {
         BlockType type = this.cache.getType(x, y, z);
         if (type != BlockType.UNKNOWN) {
            known = true;
            if (type.isStandable()) {
               return (double)y;
            }
         }
      }

      return known ? 0.0 : -1.0;
   }

   /** Casts along the current velocity for anything solid. Reads the live world, so never stale. */
   private boolean isTerrainAhead() {
      double motionX = mc.player.motionX;
      double motionY = mc.player.motionY;
      double motionZ = mc.player.motionZ;
      double lengthSq = motionX * motionX + motionY * motionY + motionZ * motionZ;
      if (lengthSq < 1.0E-8) {
         return false;
      } else {
         double scale = (double)this.settings.getLookAhead() / Math.sqrt(lengthSq);
         Vec3d start = new Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ);
         Vec3d end = new Vec3d(mc.player.posX + motionX * scale, mc.player.posY + motionY * scale, mc.player.posZ + motionZ * scale);
         RayTraceResult result = mc.world.rayTraceBlocks(start, end, false, true, false);
         return result != null && result.typeOfHit == RayTraceResult.Type.BLOCK;
      }
   }

   /**
    * Fires a rocket when the glider has run out of speed.
    *
    * Switching hotbar slot and right-clicking in the same tick races the held-item-change packet -
    * the server can process the use before the swap and consume the wrong item. Selecting the
    * firework and boosting on the following tick costs 50ms and removes the race entirely.
    */
   private void maybeBoost(double speed) {
      if (!(speed >= this.settings.getBoostSpeed()) && this.boostTimer.passedMs(this.settings.getBoostCooldownMs())) {
         if (mc.player.getHeldItemMainhand().getItem() == Items.FIREWORKS) {
            mc.playerController.processRightClick(mc.player, mc.world, EnumHand.MAIN_HAND);
            this.boostTimer.reset();
         } else {
            int slot = InventoryUtil.findItemInHotbar(Items.FIREWORKS);
            if (slot != -1) {
               mc.player.inventory.currentItem = slot;
            }
         }
      }
   }

   private void aimYaw(double dx, double dz) {
      float desired = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      float difference = MathHelper.wrapDegrees(desired - mc.player.rotationYaw);
      float speed = this.settings.getRotationSpeed();
      mc.player.rotationYaw = mc.player.rotationYaw + MathHelper.clamp(difference, -speed, speed);
   }

   private void aimPitch(float desired) {
      float difference = desired - mc.player.rotationPitch;
      float speed = this.settings.getRotationSpeed();
      mc.player.rotationPitch = MathHelper.clamp(mc.player.rotationPitch + MathHelper.clamp(difference, -speed, speed), -90.0F, 90.0F);
   }

   private double horizontalSpeed() {
      return Math.sqrt(mc.player.motionX * mc.player.motionX + mc.player.motionZ * mc.player.motionZ);
   }
}
