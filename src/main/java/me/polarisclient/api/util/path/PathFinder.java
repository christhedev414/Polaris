package me.polarisclient.api.util.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import me.polarisclient.api.util.Wrapper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/**
 * A* over block positions. A node is the position of the player's feet, and the moves are the ones
 * the player can make with no block interaction: walking, stepping up one block, and dropping down.
 *
 * The search is bounded by a node budget so it cannot stall the client. When the budget runs out
 * before the goal is reached, the path to the closest node seen so far is returned instead, which
 * lets the caller walk in the right direction and search again from there.
 */
public class PathFinder implements Wrapper {
   public static final double SQRT_2 = Math.sqrt(2.0);
   public static final double COST_WALK = 1.0;
   public static final double COST_DIAGONAL = SQRT_2;
   public static final double COST_JUMP = 1.6;
   public static final double COST_FALL_PER_BLOCK = 0.4;
   public static final double COST_WATER = 2.0;
   /** Weight applied to vertical distance in the goal heuristics. */
   public static final double COST_VERTICAL = 0.5;

   private static final EnumFacing[] DIRECTIONS = new EnumFacing[]{EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST};

   private PathFinder() {
   }

   /**
    * Octile distance: the cost of the cheapest unobstructed walk across flat ground, given that a
    * diagonal step costs sqrt(2) and a straight step costs 1.
    */
   public static double octile(int dx, int dz) {
      int ax = Math.abs(dx);
      int az = Math.abs(dz);
      int min = Math.min(ax, az);
      return (double)(Math.max(ax, az) - min) + SQRT_2 * (double)min;
   }

   public static List<BlockPos> find(BlockPos start, Goal goal, int maxNodes, int maxFall) {
      if (mc.world == null) {
         return Collections.emptyList();
      } else {
         Map<BlockPos, PathNode> nodes = new HashMap<>();
         Set<BlockPos> closed = new HashSet<>();
         PriorityQueue<PathNode> open = new PriorityQueue<>();
         PathNode startNode = new PathNode(start, null, 0.0, goal.heuristic(start));
         nodes.put(start, startNode);
         open.add(startNode);
         PathNode best = startNode;
         int expanded = 0;

         while(!open.isEmpty() && expanded < maxNodes) {
            PathNode current = open.poll();
            if (closed.add(current.pos)) {
               ++expanded;
               if (goal.isFinished(current.pos)) {
                  return trace(current);
               }

               if (current.hCost < best.hCost) {
                  best = current;
               }

               for(PathFinder.Move move : successors(current.pos, maxFall)) {
                  if (!closed.contains(move.pos)) {
                     double gCost = current.gCost + move.cost;
                     PathNode neighbour = nodes.get(move.pos);
                     if (neighbour == null) {
                        neighbour = new PathNode(move.pos, current, gCost, goal.heuristic(move.pos));
                        nodes.put(move.pos, neighbour);
                        open.add(neighbour);
                     } else if (gCost < neighbour.gCost) {
                        neighbour.gCost = gCost;
                        neighbour.parent = current;
                        open.add(neighbour);
                     }
                  }
               }
            }
         }

         return best == startNode ? Collections.emptyList() : trace(best);
      }
   }

   private static List<BlockPos> trace(PathNode node) {
      List<BlockPos> path = new ArrayList<>();

      for(PathNode step = node; step != null; step = step.parent) {
         path.add(step.pos);
      }

      Collections.reverse(path);
      return path;
   }

   private static List<PathFinder.Move> successors(BlockPos pos, int maxFall) {
      List<PathFinder.Move> moves = new ArrayList<>();
      boolean headroom = PathUtil.isPassable(pos.up(2));

      for(EnumFacing direction : DIRECTIONS) {
         addCardinal(moves, pos.offset(direction), headroom, maxFall);
      }

      for(int i = 0; i < DIRECTIONS.length; ++i) {
         EnumFacing first = DIRECTIONS[i];
         EnumFacing second = DIRECTIONS[(i + 1) % DIRECTIONS.length];
         if (PathUtil.hasClearance(pos.offset(first)) && PathUtil.hasClearance(pos.offset(second))) {
            BlockPos diagonal = pos.offset(first).offset(second);
            if (PathUtil.canStandAt(diagonal)) {
               moves.add(new PathFinder.Move(diagonal, cost(diagonal, COST_DIAGONAL)));
            }
         }
      }

      return moves;
   }

   private static void addCardinal(List<PathFinder.Move> moves, BlockPos side, boolean headroom, int maxFall) {
      if (PathUtil.canStandAt(side)) {
         moves.add(new PathFinder.Move(side, cost(side, COST_WALK)));
      } else {
         BlockPos stepUp = side.up();
         if (headroom && PathUtil.canStandAt(stepUp)) {
            moves.add(new PathFinder.Move(stepUp, cost(stepUp, COST_JUMP)));
         } else if (PathUtil.hasClearance(side)) {
            BlockPos landing = PathUtil.fallTarget(side, maxFall);
            if (landing != null) {
               int drop = side.getY() - landing.getY();
               moves.add(new PathFinder.Move(landing, cost(landing, COST_WALK + COST_FALL_PER_BLOCK * (double)drop)));
            }
         }
      }
   }

   private static double cost(BlockPos pos, double base) {
      return PathUtil.isWater(pos) ? base * COST_WATER : base;
   }

   private static class Move {
      final BlockPos pos;
      final double cost;

      Move(BlockPos pos, double cost) {
         this.pos = pos;
         this.cost = cost;
      }
   }
}
