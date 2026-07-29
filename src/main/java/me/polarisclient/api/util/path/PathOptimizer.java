package me.polarisclient.api.util.path;

import java.util.ArrayList;
import java.util.List;
import me.polarisclient.api.util.path.movement.Movement;
import me.polarisclient.api.util.path.movement.MovementType;
import me.polarisclient.api.util.path.movement.MovementWalk;

/**
 * Post-processes a raw path into one that is nicer to walk.
 *
 * A* returns a step per block, which is correct but jerky to execute: the player re-aims at every
 * block centre, and vanilla cancels sprinting each time the input direction changes appreciably.
 * Collapsing straight runs into single long movements means the player commits to a heading and can
 * actually hold a sprint.
 *
 * Only runs of IDENTICAL direction, type and height are merged. Shortcutting across corners - the
 * "string pulling" used in open-world navmesh pathing - is deliberately not attempted: a diagonal
 * shortcut that looks clear block-by-block can still clip a corner the collision system will not let
 * the player through, and the failure mode is the player grinding against a wall until the stuck
 * detector fires. The conservative merge captures nearly all of the benefit with none of that risk.
 */
public final class PathOptimizer {
   /**
    * Longest run to merge. Unbounded merges would produce single movements spanning a whole
    * corridor, which makes {@link Movement#isStillValid()} coarse and delays noticing that the route
    * has been blocked partway along.
    */
   private static final int MAX_MERGE_LENGTH = 8;

   private PathOptimizer() {
   }

   public static Path optimize(Path path) {
      if (path == null || path.size() < 2) {
         return path;
      } else {
         List<Movement> source = path.getMovements();
         List<Movement> merged = new ArrayList<>(source.size());
         int i = 0;

         while(i < source.size()) {
            Movement head = source.get(i);
            if (!isFlatWalk(head)) {
               merged.add(head);
               ++i;
            } else {
               int stepX = head.getTo().getX() - head.getFrom().getX();
               int stepZ = head.getTo().getZ() - head.getFrom().getZ();
               double cost = head.getCost();
               int end = i + 1;
               int length = 1;

               while(end < source.size() && length < MAX_MERGE_LENGTH) {
                  Movement next = source.get(end);
                  if (!isFlatWalk(next)
                     || next.getFrom().getY() != head.getFrom().getY()
                     || next.getTo().getX() - next.getFrom().getX() != stepX
                     || next.getTo().getZ() - next.getFrom().getZ() != stepZ) {
                     break;
                  }

                  cost += next.getCost();
                  ++end;
                  ++length;
               }

               merged.add(length == 1 ? head : new MovementWalk(head.getFrom(), source.get(end - 1).getTo(), cost));
               i = end;
            }
         }

         return merged.size() == source.size() ? path : new Path(path.getStart(), merged, path.isPartial());
      }
   }

   private static boolean isFlatWalk(Movement movement) {
      return movement.getType() == MovementType.WALK && movement.getFrom().getY() == movement.getTo().getY();
   }
}
