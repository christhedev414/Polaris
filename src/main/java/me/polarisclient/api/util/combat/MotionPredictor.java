package me.polarisclient.api.util.combat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import me.polarisclient.api.util.Wrapper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/**
 * Forward-simulates where a player will be in a few ticks.
 *
 * Placement modules are always shooting behind a moving target: by the time a placement packet
 * reaches the server the player has moved on, so aiming at where they are now is aiming at where
 * they were. This walks their current motion forward through a simplified version of the vanilla
 * movement rules and reports where they are likely to be when the packet lands.
 *
 * The simulation is deliberately optimistic about collision and pessimistic about lookahead. It
 * ramps the prediction distance up only while a player keeps moving consistently, and drops it to
 * zero the instant they stop or change direction - a wrong prediction is worse than no prediction,
 * because it places a bed somewhere the target was never going to be.
 */
public class MotionPredictor implements Wrapper {
   /** Yaw value meaning "no previous heading recorded". Real yaws are always within +/-180. */
   public static final float NO_YAW = 512.0F;
   /** Below this, a player counts as stationary and their heading is not recomputed. */
   private static final double MOVING_EPSILON = 1.0E-4;
   /** Horizontal offsets probed alongside the centre line, approximating the player's width. */
   private static final double[][] PROBE_OFFSETS = new double[][]{{0.3, 0.3}, {0.3, -0.3}, {-0.3, 0.3}, {-0.3, -0.3}};
   /** Assumed round-trip when the server has not reported a ping yet. */
   private static final int DEFAULT_PING_MS = 50;

   private final Map<EntityPlayer, MotionPredictor.MoveRotation> tracking = new HashMap<>();

   private int maxLookahead = 10;
   private int lookaheadIncrement = 4;
   private boolean latencySync;
   private boolean verticalExtrapolation = true;
   private boolean axisSplit = true;
   private boolean holeExitClamp;
   private boolean elevatedHoleClamp;
   private boolean stepCorrection;
   private int maxSteps = 2;
   private double stepSpeedThreshold = 0.3;
   private double directionResetAngle = 15.0;
   private int airborneThreshold = 39;
   private int gravityRamp = 2;
   private int fallAcceleration = 2;
   private int fallCurve = 1;

   // ---- configuration ----

   public void configure(
      int maxLookahead,
      int lookaheadIncrement,
      boolean latencySync,
      boolean verticalExtrapolation,
      boolean axisSplit,
      boolean holeExitClamp,
      boolean elevatedHoleClamp,
      boolean stepCorrection,
      int maxSteps,
      double stepSpeedThreshold,
      double directionResetAngle,
      int airborneThreshold,
      int gravityRamp,
      int fallAcceleration,
      int fallCurve
   ) {
      this.maxLookahead = maxLookahead;
      this.lookaheadIncrement = lookaheadIncrement;
      this.latencySync = latencySync;
      this.verticalExtrapolation = verticalExtrapolation;
      this.axisSplit = axisSplit;
      this.holeExitClamp = holeExitClamp;
      this.elevatedHoleClamp = elevatedHoleClamp;
      this.stepCorrection = stepCorrection;
      this.maxSteps = maxSteps;
      this.stepSpeedThreshold = stepSpeedThreshold;
      this.directionResetAngle = directionResetAngle;
      this.airborneThreshold = airborneThreshold;
      this.gravityRamp = gravityRamp;
      this.fallAcceleration = fallAcceleration;
      this.fallCurve = fallCurve;
   }

   public void clear() {
      this.tracking.clear();
   }

   /**
    * Ceiling on how far ahead to simulate.
    *
    * With latency sync on this becomes a function of ping: predicting further than the packet's
    * round trip means aiming past where the target will be when the server acts on it, which
    * overshoots just as badly as not predicting at all.
    */
   public int getMaxPredictTicks() {
      if (!this.latencySync) {
         return this.maxLookahead;
      } else {
         int ping = this.getPing();
         return Math.min(ping * 2 / 50, this.maxLookahead);
      }
   }

   private int getPing() {
      try {
         if (mc.getConnection() != null) {
            net.minecraft.client.network.NetworkPlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUniqueID());
            if (info != null && info.getResponseTime() > 0) {
               return info.getResponseTime();
            }
         }
      } catch (Exception var2) {
      }

      return DEFAULT_PING_MS;
   }

   // ---- tracking ----

   /** Refreshes the heading and lookahead of every player within range. Main thread, once per tick. */
   public void update(double range) {
      if (mc.world != null && mc.player != null) {
         double rangeSq = range * range;
         Iterator<Map.Entry<EntityPlayer, MotionPredictor.MoveRotation>> iterator = this.tracking.entrySet().iterator();

         while(iterator.hasNext()) {
            EntityPlayer tracked = iterator.next().getKey();
            if (tracked.isDead || tracked.getDistanceSq(mc.player) > rangeSq || !mc.world.playerEntities.contains(tracked)) {
               iterator.remove();
            }
         }

         for(EntityPlayer player : mc.world.playerEntities) {
            if (player != mc.player && !player.isDead && !(player.getDistanceSq(mc.player) > rangeSq)) {
               this.tracking.put(player, this.calculateMoveRotation(player, this.tracking.get(player)));
            }
         }
      }
   }

   /**
    * Derives a heading from the last tick's movement and ramps the lookahead.
    *
    * The ramp is the whole point. A player who has been running in a straight line for a second is
    * highly predictable; one who just changed direction is not predictable at all. So confidence -
    * expressed as how many ticks ahead we are willing to simulate - accumulates while the heading
    * holds and is thrown away entirely the moment it does not.
    */
   private MotionPredictor.MoveRotation calculateMoveRotation(EntityPlayer player, MotionPredictor.MoveRotation previous) {
      double dx = player.posX - player.lastTickPosX;
      double dz = player.posZ - player.lastTickPosZ;
      double speed = Math.sqrt(dx * dx + dz * dz);
      float previousYaw = previous == null ? NO_YAW : previous.yaw;
      float yaw;
      if (speed < MOVING_EPSILON) {
         // Stationary: keep the last heading rather than inventing one from noise.
         yaw = previousYaw;
      } else {
         yaw = (float)Math.toDegrees(Math.atan2(dz, dx));
      }

      int lookahead = 0;
      if (speed >= MOVING_EPSILON && previous != null && previousYaw != NO_YAW && yaw != NO_YAW) {
         float difference = MathHelper.wrapDegrees(yaw - previousYaw);
         if ((double)Math.abs(difference) <= this.directionResetAngle) {
            lookahead = Math.min(this.getMaxPredictTicks(), previous.lookahead + this.lookaheadIncrement);
         }
      }

      return new MotionPredictor.MoveRotation(yaw, previousYaw, lookahead);
   }

   /** How many ticks ahead this player may currently be predicted, given how consistently they move. */
   public int getLookahead(EntityPlayer player, int baseLookahead) {
      MotionPredictor.MoveRotation rotation = this.tracking.get(player);
      int ramped = rotation == null ? 0 : rotation.lookahead;
      return Math.min(this.getMaxPredictTicks(), baseLookahead + ramped);
   }

   /** Magnitude of the player's last-tick movement, used to gate base placement on a moving target. */
   public double getSpeed(EntityPlayer player) {
      double dx = player.posX - player.lastTickPosX;
      double dz = player.posZ - player.lastTickPosZ;
      return Math.sqrt(dx * dx + dz * dz);
   }

   // ---- simulation ----

   /** Where this entity is likely to be in {@code ticks} ticks. */
   public Vec3d predict(EntityPlayer target, int ticks) {
      double motionX = target.posX - target.lastTickPosX;
      double motionY = target.posY - target.lastTickPosY;
      double motionZ = target.posZ - target.lastTickPosZ;
      double x = target.posX;
      double y = target.posY;
      double z = target.posZ;
      AxisAlignedBB box = target.getEntityBoundingBox();
      double width = (box.maxX - box.minX) / 2.0;
      double height = box.maxY - box.minY;

      // A player climbing out of a hole is about to be one block higher and then level off. Letting
      // the ordinary gravity loop run through that transition predicts them still rising, which
      // lands the bed a block too high.
      boolean clamped = false;
      if (this.holeExitClamp && motionY > 0.2 && this.isInEnclosedHole(target)) {
         y += 1.0;
         clamped = true;
      }

      double nudge = (double)this.fallAcceleration / Math.pow(10.0, (double)this.fallCurve);
      double velocityCap = (double)this.airborneThreshold / Math.pow(10.0, (double)this.gravityRamp);
      double velocityY = motionY;
      int stepsUsed = 0;

      for(int tick = 0; tick < ticks; ++tick) {
         boolean stepped = false;
         if (this.axisSplit) {
            // Sliding along a wall is the common case, and only per-axis testing reproduces it -
            // a joint test would stop the prediction dead at the first corner.
            if (isPathClear(x, y, z, x + motionX, y, z, width, height)) {
               x += motionX;
            } else if (this.stepCorrection && stepsUsed < this.maxSteps && Math.abs(motionX) > this.stepSpeedThreshold) {
               double raised = this.tryStep(x, y, z, x + motionX, z, width, height);
               if (raised > y) {
                  y = raised;
                  x += motionX;
                  ++stepsUsed;
                  stepped = true;
               }
            }

            if (isPathClear(x, y, z, x, y, z + motionZ, width, height)) {
               z += motionZ;
            } else if (this.stepCorrection && stepsUsed < this.maxSteps && Math.abs(motionZ) > this.stepSpeedThreshold) {
               double raised = this.tryStep(x, y, z, x, z + motionZ, width, height);
               if (raised > y) {
                  y = raised;
                  z += motionZ;
                  ++stepsUsed;
                  stepped = true;
               }
            }
         } else if (isPathClear(x, y, z, x + motionX, y, z + motionZ, width, height)) {
            x += motionX;
            z += motionZ;
         } else if (this.stepCorrection
            && stepsUsed < this.maxSteps
            && Math.sqrt(motionX * motionX + motionZ * motionZ) > this.stepSpeedThreshold) {
            double raised = this.tryStep(x, y, z, x + motionX, z + motionZ, width, height);
            if (raised > y) {
               y = raised;
               x += motionX;
               z += motionZ;
               ++stepsUsed;
               stepped = true;
            }
         }

         // A step already moved the entity vertically this tick; applying gravity on top of it
         // would double-count the movement.
         if (this.verticalExtrapolation && !clamped && !stepped) {
            if (target.isInWater() || target.isInLava() || target.isElytraFlying()) {
               // In a fluid or under wings the entity controls its own vertical motion, so the
               // measured value is already the truth and gravity does not apply.
               if (isPathClear(x, y, z, x, y + velocityY, z, width, height)) {
                  y += velocityY;
               }
            } else {
               velocityY -= nudge;
               if (Math.abs(velocityY) > velocityCap) {
                  velocityY = -nudge;
               }

               if (isPathClear(x, y, z, x, y + velocityY, z, width, height)) {
                  y += velocityY;
               } else {
                  velocityY += nudge;
               }
            }
         }
      }

      return new Vec3d(x, y, z);
   }

   /** Returns the raised Y if a one or two block step-up would clear the obstruction, else the old Y. */
   private double tryStep(double x, double y, double z, double toX, double toZ, double width, double height) {
      for(int rise = 1; rise <= 2; ++rise) {
         double raised = y + (double)rise;
         if (isPathClear(x, raised, z, toX, raised, toZ, width, height)) {
            return raised;
         }
      }

      return y;
   }

   /**
    * Whether the entity is standing in a one-block hole with walls on all four sides.
    *
    * Elevated clamp extends the same test one block lower, catching the case where the player is
    * mid-jump out of the hole rather than still sitting in it.
    */
   private boolean isInEnclosedHole(EntityPlayer target) {
      BlockPos base = new BlockPos(target.posX, target.posY, target.posZ);
      if (this.isEnclosed(base)) {
         return true;
      } else {
         return this.elevatedHoleClamp && this.isEnclosed(base.down());
      }
   }

   private boolean isEnclosed(BlockPos pos) {
      if (!mc.world.getBlockState(pos.up()).getMaterial().blocksMovement()) {
         for(net.minecraft.util.EnumFacing facing : net.minecraft.util.EnumFacing.HORIZONTALS) {
            if (!mc.world.getBlockState(pos.offset(facing)).getMaterial().blocksMovement()) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   /**
    * Whether a body of the given size can travel between two points without hitting anything.
    *
    * Traces the centre line plus four corner offsets rather than sweeping a full bounding box. A
    * real swept-AABB test is more accurate but far more expensive, and this runs for every tick of
    * every candidate prediction; five rays reproduce the outcome closely enough that the difference
    * never shows up in a placement decision.
    */
   public static boolean isPathClear(double fromX, double fromY, double fromZ, double toX, double toY, double toZ, double width, double height) {
      if (!isLineClear(fromX, fromY, fromZ, toX, toY, toZ)) {
         return false;
      } else {
         for(double[] offset : PROBE_OFFSETS) {
            if (!isLineClear(fromX + offset[0], fromY, fromZ + offset[1], toX + offset[0], toY, toZ + offset[1])) {
               return false;
            }
         }

         return true;
      }
   }

   private static boolean isLineClear(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
      if (mc.world == null) {
         return false;
      } else {
         RayTraceResult result = mc.world
            .rayTraceBlocks(new Vec3d(fromX, fromY, fromZ), new Vec3d(toX, toY, toZ), false, true, false);
         return result == null || result.typeOfHit != RayTraceResult.Type.BLOCK;
      }
   }

   /** A player's movement heading for one tick, plus how far ahead we currently trust it. */
   public static final class MoveRotation {
      public final float yaw;
      public final float lastYaw;
      public final int lookahead;

      public MoveRotation(float yaw, float lastYaw, int lookahead) {
         this.yaw = yaw;
         this.lastYaw = lastYaw;
         this.lookahead = lookahead;
      }
   }
}
