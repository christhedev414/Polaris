package me.polarisclient.api.util.path.calc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import me.polarisclient.api.util.path.Path;
import me.polarisclient.api.util.path.goal.Goal;
import me.polarisclient.api.util.path.movement.Movement;
import me.polarisclient.api.util.path.movement.MovementCosts;
import me.polarisclient.api.util.path.movement.MovementGenerator;
import me.polarisclient.api.util.path.movement.MovementType;
import me.polarisclient.api.util.path.world.BlockCache;
import net.minecraft.util.math.BlockPos;

/**
 * The A* search.
 *
 * Deliberately knows nothing about Minecraft. It asks {@link MovementGenerator} for successors and
 * a {@link Goal} for distance estimates, and reads terrain only through a {@link BlockCache}
 * snapshot. That is what makes it safe to run off the main thread, and it is why parkour, swimming
 * and climbing were all added without touching this file.
 *
 * Two properties matter for using it in a game loop:
 *
 *   - It is BOUNDED, by both node count and wall-clock time. An unreachable goal makes A* explore
 *     everything it can reach, which for a Minecraft world means hundreds of thousands of nodes; a
 *     search that can run forever will eventually be a search that hangs the client.
 *
 *   - When the budget runs out it returns the best partial route rather than nothing. The closest
 *     node found so far is almost always in the right general direction, so the player keeps moving
 *     while the next segment is planned. This is also what lets a bounded search cover unbounded
 *     distances.
 */
public final class PathCalculator {
   /** Cap on positions recorded for the debug overlay, so a big search cannot exhaust memory. */
   private static final int DEBUG_NODE_LIMIT = 4000;
   /** How often to consult the clock, in expansions. Checking every iteration is itself a cost. */
   private static final int TIME_CHECK_INTERVAL = 256;

   private PathCalculator() {
   }

   public static CalculationResult calculate(
      BlockCache cache, BlockPos start, Goal goal, MovementCosts costs, int maxNodes, long timeoutMs, boolean debug
   ) {
      long began = System.currentTimeMillis();
      LongNodeMap nodes = new LongNodeMap(Math.min(maxNodes, 65536));
      BinaryHeap open = new BinaryHeap(1024);
      List<BlockPos> explored = debug ? new ArrayList<>() : Collections.emptyList();

      PathNode startNode = new PathNode(start.getX(), start.getY(), start.getZ());
      startNode.gCost = 0.0;
      startNode.hCost = goal.heuristic(startNode.x, startNode.y, startNode.z) * costs.getHeuristicScale();
      startNode.fCost = startNode.hCost;
      nodes.put(startNode.key, startNode);
      open.insert(startNode);

      PathCalculator.Expander expander = new PathCalculator.Expander(nodes, open, goal, costs.getHeuristicScale());
      // Best-so-far, used when the budget runs out. Ties on heuristic break toward the cheaper
      // route, which keeps partial paths from wandering.
      PathNode best = startNode;
      PathNode reached = null;
      int expanded = 0;

      while(!open.isEmpty() && expanded < maxNodes) {
         PathNode current = open.poll();
         // A node can be queued more than once only via decrease-key, which never re-queues a
         // closed node, but guard anyway - the cost is one predictable branch.
         if (current.closed) {
            continue;
         }

         current.closed = true;
         ++expanded;
         if (debug && explored.size() < DEBUG_NODE_LIMIT) {
            explored.add(new BlockPos(current.x, current.y, current.z));
         }

         if (goal.isFinished(current.x, current.y, current.z)) {
            reached = current;
            break;
         }

         if (current.hCost < best.hCost || current.hCost == best.hCost && current.gCost < best.gCost) {
            best = current;
         }

         if ((expanded & TIME_CHECK_INTERVAL - 1) == 0 && System.currentTimeMillis() - began > timeoutMs) {
            break;
         }

         expander.current = current;
         MovementGenerator.generate(cache, current.x, current.y, current.z, costs, expander);
      }

      long duration = System.currentTimeMillis() - began;
      PathNode target = reached != null ? reached : best;
      // Never moved: walled in, or standing in terrain the cache has not seen yet.
      if (target == startNode) {
         return CalculationResult.failed(expanded, duration, explored);
      } else {
         Path path = reconstruct(target, reached == null);
         CalculationResult.Status status = reached != null ? CalculationResult.Status.SUCCESS : CalculationResult.Status.PARTIAL;
         return new CalculationResult(status, path, expanded, duration, explored);
      }
   }

   /**
    * Walks the parent chain back to the start and materialises the {@link Movement} objects.
    *
    * This is the only place Movements are constructed - the search itself carried just a
    * MovementType per node, so the tens of thousands of edges that were considered and rejected
    * cost nothing.
    */
   private static Path reconstruct(PathNode target, boolean partial) {
      Deque<PathNode> stack = new ArrayDeque<>();

      for(PathNode node = target; node != null; node = node.parent) {
         stack.push(node);
      }

      PathNode previous = stack.pop();
      BlockPos start = new BlockPos(previous.x, previous.y, previous.z);
      List<Movement> movements = new ArrayList<>(stack.size());

      while(!stack.isEmpty()) {
         PathNode node = stack.pop();
         BlockPos from = new BlockPos(previous.x, previous.y, previous.z);
         BlockPos to = new BlockPos(node.x, node.y, node.z);
         MovementType type = node.movement == null ? MovementType.WALK : node.movement;
         movements.add(Movement.create(type, from, to, node.gCost - previous.gCost));
         previous = node;
      }

      return new Path(start, movements, partial);
   }

   /**
    * Relaxes one edge. Kept as a reusable object rather than a lambda because it needs a mutable
    * "current node" field, and because reusing one instance across the whole search means the hot
    * loop allocates nothing at all.
    */
   private static final class Expander implements MovementGenerator.MovementSink {
      private final LongNodeMap nodes;
      private final BinaryHeap open;
      private final Goal goal;
      private final double heuristicScale;
      PathNode current;

      Expander(LongNodeMap nodes, BinaryHeap open, Goal goal, double heuristicScale) {
         this.nodes = nodes;
         this.open = open;
         this.goal = goal;
         this.heuristicScale = heuristicScale;
      }

      @Override
      public void accept(int x, int y, int z, double cost, MovementType type) {
         long key = PathNode.key(x, y, z);
         PathNode node = this.nodes.get(key);
         if (node == null) {
            node = new PathNode(x, y, z);
            this.nodes.put(key, node);
         } else if (node.closed) {
            return;
         }

         double tentative = this.current.gCost + cost;
         if (tentative < node.gCost) {
            node.gCost = tentative;
            node.parent = this.current;
            node.movement = type;
            if (node.heapIndex < 0) {
               // First time this node has been reachable: estimate distance and queue it.
               node.hCost = this.goal.heuristic(x, y, z) * this.heuristicScale;
               node.fCost = tentative + node.hCost;
               this.open.insert(node);
            } else {
               // Already queued and we just found a cheaper route - this is the decrease-key that
               // PriorityQueue cannot express.
               node.fCost = tentative + node.hCost;
               this.open.decreaseKey(node);
            }
         }
      }
   }
}
