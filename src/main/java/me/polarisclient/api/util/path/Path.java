package me.polarisclient.api.util.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import me.polarisclient.api.util.path.movement.Movement;
import net.minecraft.util.math.BlockPos;

/**
 * A finished route: an ordered list of {@link Movement}s from a start position to an end position.
 *
 * Immutable, because it is produced on the planning thread and consumed on the main thread. Handing
 * the executor something it cannot modify removes any question of the two threads sharing mutable
 * state - publication through a single volatile reference in
 * {@link me.polarisclient.api.util.path.PathManager} is then all the synchronisation needed.
 */
public class Path {
   private final BlockPos start;
   private final List<Movement> movements;
   private final boolean partial;
   private final double totalCost;

   public Path(BlockPos start, List<Movement> movements, boolean partial) {
      this.start = start;
      this.movements = Collections.unmodifiableList(new ArrayList<>(movements));
      this.partial = partial;
      double cost = 0.0;

      for(Movement movement : this.movements) {
         cost += movement.getCost();
      }

      this.totalCost = cost;
   }

   public BlockPos getStart() {
      return this.start;
   }

   public List<Movement> getMovements() {
      return this.movements;
   }

   public Movement get(int index) {
      return this.movements.get(index);
   }

   public int size() {
      return this.movements.size();
   }

   public boolean isEmpty() {
      return this.movements.isEmpty();
   }

   public BlockPos getEnd() {
      return this.movements.isEmpty() ? this.start : this.movements.get(this.movements.size() - 1).getTo();
   }

   /**
    * Whether the search ran out of budget before reaching the goal. A partial path still points the
    * right way, and the manager re-plans from its far end - which is how arbitrarily long journeys
    * are covered without ever running an unbounded search.
    */
   public boolean isPartial() {
      return this.partial;
   }

   public double getTotalCost() {
      return this.totalCost;
   }

   /** Every block position along the route, start included. Used by the renderer. */
   public List<BlockPos> getPositions() {
      List<BlockPos> positions = new ArrayList<>(this.movements.size() + 1);
      positions.add(this.start);

      for(Movement movement : this.movements) {
         positions.add(movement.getTo());
      }

      return positions;
   }
}
