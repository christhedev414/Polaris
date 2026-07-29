package me.polarisclient.api.util.path;

import me.polarisclient.api.util.Wrapper;
import me.polarisclient.api.util.path.movement.Movement;
import me.polarisclient.api.util.path.movement.MovementContext;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Walks the player along a {@link Path}, one movement at a time.
 *
 * This is the half of the system that deals with the world actually being messy. The plan assumes
 * the player teleports cleanly from block centre to block centre; reality involves momentum,
 * knockback, mobs, slippery ice and terrain that changed since the plan was made. The executor's job
 * is to notice when reality has diverged from the plan and say so, rather than to keep pushing a
 * plan that no longer applies.
 *
 * It reports failure instead of repathing itself. Deciding to re-plan is
 * {@link PathManager}'s call - it owns the goal and the worker thread - which keeps this class a
 * pure state machine over one fixed path.
 */
public class PathExecutor implements Wrapper {
   /** How far off the current target the player may drift before we try to recover. */
   private static final double MAX_DEVIATION = 4.0;
   /** Radius within which we will re-attach to a path node rather than give up. */
   private static final double REATTACH_RADIUS = 3.0;
   /** Ticks of no measurable progress before declaring the movement stuck. */
   private static final int STUCK_TICKS = 60;
   /** Nodes to scan ahead when checking arrival, to absorb corner-cutting and overshoot. */
   private static final int LOOKAHEAD = 3;
   /** Improvement in blocks that counts as real progress, above per-tick jitter. */
   private static final double PROGRESS_EPSILON = 0.05;

   private final Path path;
   private final RotationController rotation;
   private PathExecutor.State state = PathExecutor.State.RUNNING;
   private int index;
   private double bestDistance = Double.MAX_VALUE;
   private int stuckTicks;

   public PathExecutor(Path path, RotationController rotation) {
      this.path = path;
      this.rotation = rotation;
   }

   public PathExecutor.State getState() {
      return this.state;
   }

   public Path getPath() {
      return this.path;
   }

   public int getIndex() {
      return this.index;
   }

   /** Whether we are close enough to the end of a partial path that the next segment should be planned. */
   public boolean isNearEnd() {
      return this.index >= this.path.size() - 2;
   }

   public Movement getCurrentMovement() {
      return this.index >= 0 && this.index < this.path.size() ? this.path.get(this.index) : null;
   }

   /**
    * Advances the state machine and drives the player for one tick. Called from the input event, so
    * that reading and writing movement input happen at the same point in the tick.
    */
   public void tick(MovementInput input, boolean sprintAllowed) {
      if (this.state == PathExecutor.State.RUNNING) {
         if (mc.player == null) {
            this.state = PathExecutor.State.FAILED;
         } else {
            BlockPos feet = feetOf();
            this.consumeReachedMovements(feet);
            if (this.index >= this.path.size()) {
               this.state = PathExecutor.State.COMPLETE;
            } else {
               Movement movement = this.path.get(this.index);
               // The world may have changed since planning: a door shut, a block placed in the gap.
               if (!movement.isStillValid()) {
                  this.state = PathExecutor.State.FAILED;
               } else {
                  double distance = horizontalDistanceTo(movement.getTo());
                  if (distance > MAX_DEVIATION && !this.tryReattach(feet)) {
                     this.state = PathExecutor.State.FAILED;
                  } else if (this.updateProgress(distance)) {
                     this.state = PathExecutor.State.FAILED;
                  } else {
                     MovementContext context = new MovementContext(mc.player, input, this.rotation, sprintAllowed);
                     this.path.get(this.index).execute(context);
                     // Applied once, after the movement has chosen its heading, so a tick never
                     // contains two competing turns.
                     this.rotation.update();
                  }
               }
            }
         }
      }
   }

   /**
    * Skips past every movement already completed.
    *
    * Scanning a few nodes ahead rather than only the current one is what absorbs the normal
    * imprecision of real movement: cutting a corner diagonally, or overshooting a short step while
    * sprinting, can land the player on a later node without the current one ever registering.
    * Without the lookahead that reads as being off-path and forces a needless recalculation.
    */
   private void consumeReachedMovements(BlockPos feet) {
      for(int offset = 0; offset <= LOOKAHEAD && this.index + offset < this.path.size(); ++offset) {
         if (this.path.get(this.index + offset).isComplete(feet)) {
            this.index += offset + 1;
            this.resetProgress();
            return;
         }
      }
   }

   /**
    * After knockback, a failed jump or an unexpected fall, looks for a nearby node on the remaining
    * path to rejoin. Recovering locally is much cheaper than a full re-plan and keeps the player
    * moving instead of stalling.
    */
   private boolean tryReattach(BlockPos feet) {
      int bestIndex = -1;
      double bestSq = REATTACH_RADIUS * REATTACH_RADIUS;

      for(int i = this.index; i < this.path.size(); ++i) {
         double distanceSq = feet.distanceSq(this.path.get(i).getTo());
         if (distanceSq < bestSq) {
            bestSq = distanceSq;
            bestIndex = i;
         }
      }

      if (bestIndex < 0) {
         return false;
      } else {
         this.index = bestIndex;
         this.resetProgress();
         return true;
      }
   }

   /** Tracks closest approach to the current target. Returns true once the player is judged stuck. */
   private boolean updateProgress(double distance) {
      if (distance < this.bestDistance - PROGRESS_EPSILON) {
         this.bestDistance = distance;
         this.stuckTicks = 0;
         return false;
      } else {
         return ++this.stuckTicks > STUCK_TICKS;
      }
   }

   private void resetProgress() {
      this.bestDistance = Double.MAX_VALUE;
      this.stuckTicks = 0;
   }

   private static double horizontalDistanceTo(BlockPos pos) {
      double dx = (double)pos.getX() + 0.5 - mc.player.posX;
      double dz = (double)pos.getZ() + 0.5 - mc.player.posZ;
      return Math.sqrt(dx * dx + dz * dz);
   }

   public static BlockPos feetOf() {
      return new BlockPos(MathHelper.floor(mc.player.posX), MathHelper.floor(mc.player.posY), MathHelper.floor(mc.player.posZ));
   }

   public enum State {
      /** Still walking the path. */
      RUNNING,
      /** Ran the path to its end. */
      COMPLETE,
      /** Blocked, stuck or too far off course - the manager should re-plan. */
      FAILED;
   }
}
