package me.polarisclient.api.util.path.goal;

import net.minecraft.util.math.BlockPos;

/**
 * What the pathfinder is trying to reach.
 *
 * A goal answers two questions: "am I there yet" and "roughly how far is it". Keeping those behind
 * an interface is what lets one A* implementation serve "walk to this block", "get to this column",
 * "reach this Y level" and "follow that player" without any special cases in the search itself.
 *
 * Coordinates are passed as primitives rather than BlockPos because these are called on every node
 * the search touches, and allocating there would dominate the profile.
 *
 * Implementations must be safe to call from the planning thread. Anything derived from live game
 * state (an entity's position, say) has to be snapshotted on the main thread first - see
 * {@link GoalEntity#refresh()}.
 */
public interface Goal {
   /** Whether standing at this block position satisfies the goal. */
   boolean isFinished(int x, int y, int z);

   /**
    * Optimistic remaining distance in BLOCKS, not in cost units. The calculator scales this by the
    * cheapest per-block movement cost, which keeps the estimate admissible however the costs are
    * configured - so A* still returns an optimal path rather than merely a plausible one.
    */
   double heuristic(int x, int y, int z);

   /** Where to draw the goal marker in the debug renderer. */
   BlockPos getRenderPos();

   /**
    * Diagonal (octile) distance across the XZ plane: the length of the shortest unobstructed walk
    * when a diagonal step covers sqrt(2) blocks and a straight step covers 1.
    */
   static double octile(int dx, int dz) {
      int ax = Math.abs(dx);
      int az = Math.abs(dz);
      int min = Math.min(ax, az);
      return (double)(Math.max(ax, az) - min) + 1.4142135623730951 * (double)min;
   }
}
