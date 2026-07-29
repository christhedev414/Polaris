package me.polarisclient.api.util.path.movement;

import me.polarisclient.api.util.Wrapper;
import me.polarisclient.api.util.path.world.BlockType;
import net.minecraft.util.math.BlockPos;

/**
 * One step of a path: how to get from one block position to the next, and what that costs.
 *
 * Each subclass owns both halves of its behaviour - the planner's notion of cost and the tick-by-tick
 * state machine that actually performs it. Keeping those together is why adding parkour required no
 * change to {@link me.polarisclient.api.util.path.calc.PathCalculator} at all.
 *
 * Note the asymmetry with the planner: movements validate against the LIVE world, because they run
 * on the main thread and need current truth. The planner reads a snapshot. A movement that was
 * planned over terrain which has since changed will fail {@link #isStillValid()} and trigger a
 * recalculation.
 */
public abstract class Movement implements Wrapper {
   protected final BlockPos from;
   protected final BlockPos to;
   protected final double cost;

   protected Movement(BlockPos from, BlockPos to, double cost) {
      this.from = from;
      this.to = to;
      this.cost = cost;
   }

   /**
    * Builds the concrete movement for a planned edge. The planner records only a {@link MovementType}
    * per node while searching; these objects are created once, during path reconstruction.
    */
   public static Movement create(MovementType type, BlockPos from, BlockPos to, double cost) {
      switch(type) {
         case ASCEND:
            return new MovementAscend(from, to, cost);
         case DESCEND:
            return new MovementDescend(from, to, cost);
         case SWIM:
            return new MovementSwim(from, to, cost);
         case CLIMB:
            return new MovementClimb(from, to, cost);
         case PARKOUR:
            return new MovementParkour(from, to, cost);
         case WALK:
         default:
            return new MovementWalk(from, to, cost);
      }
   }

   public abstract MovementType getType();

   /** Drives the player for one tick. */
   public abstract void execute(MovementContext context);

   /**
    * Whether this step is still traversable in the world as it stands now. Checked every tick, so
    * a door closing or a block being placed across the route is noticed within a tick rather than
    * being walked into indefinitely.
    */
   public boolean isStillValid() {
      return canStandAt(this.to);
   }

   /** Whether the player has arrived. */
   public boolean isComplete(BlockPos feet) {
      return feet.equals(this.to);
   }

   public BlockPos getFrom() {
      return this.from;
   }

   public BlockPos getTo() {
      return this.to;
   }

   public double getCost() {
      return this.cost;
   }

   public double getTargetX() {
      return (double)this.to.getX() + 0.5;
   }

   public double getTargetZ() {
      return (double)this.to.getZ() + 0.5;
   }

   /** Horizontal distance from the player to the centre of the destination block. */
   protected double horizontalDistanceToTarget() {
      double dx = this.getTargetX() - mc.player.posX;
      double dz = this.getTargetZ() - mc.player.posZ;
      return Math.sqrt(dx * dx + dz * dz);
   }

   /** Horizontal distance from the player to the centre of the block they started from. */
   protected double horizontalDistanceFromStart() {
      double dx = (double)this.from.getX() + 0.5 - mc.player.posX;
      double dz = (double)this.from.getZ() + 0.5 - mc.player.posZ;
      return Math.sqrt(dx * dx + dz * dz);
   }

   // ---- live world queries, main thread only ----

   protected static BlockType typeAt(BlockPos pos) {
      return mc.world == null ? BlockType.UNKNOWN : BlockType.classify(mc.world.getBlockState(pos), mc.world, pos);
   }

   /** Whether the player's two-block body fits here. */
   protected static boolean hasClearance(BlockPos pos) {
      return typeAt(pos).isPassable() && typeAt(pos.up()).isPassable();
   }

   /** Whether the player can hold this position: solid ground below, or water/ladder inside. */
   protected static boolean canStandAt(BlockPos pos) {
      if (!hasClearance(pos)) {
         return false;
      } else {
         return typeAt(pos.down()).isStandable() || typeAt(pos).isSupporting();
      }
   }

   @Override
   public String toString() {
      return this.getType() + " " + this.from.getX() + "," + this.from.getY() + "," + this.from.getZ()
         + " -> " + this.to.getX() + "," + this.to.getY() + "," + this.to.getZ();
   }
}
