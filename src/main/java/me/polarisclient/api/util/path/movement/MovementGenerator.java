package me.polarisclient.api.util.path.movement;

import me.polarisclient.api.util.path.world.BlockCache;
import me.polarisclient.api.util.path.world.BlockType;

/**
 * The rules of locomotion: given a block position, which neighbouring positions can the player
 * reach, by what kind of movement, and at what price.
 *
 * This is the only place that knows how a Minecraft player moves, which keeps the A* implementation
 * completely generic - it just asks for successors. Everything here reads from a {@link BlockCache}
 * snapshot and never touches the live world, so it is safe to run on the planning thread.
 *
 * Successors are pushed through a {@link MovementSink} rather than returned in a list. This is the
 * hottest loop in the whole system - it runs for every node A* expands, which can be tens of
 * thousands per search - and allocating a list plus an edge object per candidate there dominated
 * the profile. Nothing is allocated per edge now.
 */
public class MovementGenerator {
   private static final double SQRT_2 = 1.4142135623730951;
   /** North, east, south, west. Consecutive pairs are adjacent, which the diagonal rule relies on. */
   private static final int[][] CARDINALS = new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

   private MovementGenerator() {
   }

   /** Receives each reachable neighbour. */
   public interface MovementSink {
      void accept(int x, int y, int z, double cost, MovementType type);
   }

   public static void generate(BlockCache cache, int x, int y, int z, MovementCosts costs, MovementSink sink) {
      BlockType current = cache.getType(x, y, z);
      boolean headroom = cache.getType(x, y + 2, z).isPassable();
      generateHorizontal(cache, x, y, z, costs, sink, headroom);
      if (costs.isDiagonalAllowed()) {
         generateDiagonal(cache, x, y, z, costs, sink);
      }

      if (costs.isSwimAllowed() && current == BlockType.WATER) {
         generateSwim(cache, x, y, z, costs, sink);
      }

      if (costs.isClimbAllowed() && current == BlockType.CLIMBABLE) {
         generateClimb(cache, x, y, z, costs, sink);
      }

      if (costs.isParkourAllowed() && headroom && cache.getType(x, y - 1, z).isStandable()) {
         generateParkour(cache, x, y, z, costs, sink);
      }
   }

   /**
    * The three ways to reach an adjacent column: walk onto it, step up onto it, or fall off into it.
    * They are mutually exclusive and tried in that order, cheapest first.
    */
   private static void generateHorizontal(BlockCache cache, int x, int y, int z, MovementCosts costs, MovementSink sink, boolean headroom) {
      for(int[] direction : CARDINALS) {
         int nx = x + direction[0];
         int nz = z + direction[1];
         if (canStand(cache, nx, y, nz)) {
            emitGroundMove(cache, sink, nx, y, nz, costs, costs.getWalk());
         } else if (headroom && canStand(cache, nx, y + 1, nz)) {
            sink.accept(nx, y + 1, nz, (costs.getWalk() + costs.getJump()) * penalty(cache, nx, y + 1, nz, costs), MovementType.ASCEND);
         } else if (hasClearance(cache, nx, y, nz)) {
            generateFall(cache, nx, y, nz, costs, sink);
         }
      }
   }

   /** Walks off an edge and looks for somewhere survivable to land. */
   private static void generateFall(BlockCache cache, int x, int y, int z, MovementCosts costs, MovementSink sink) {
      for(int drop = 1; drop <= costs.getMaxFall(); ++drop) {
         int fallY = y - drop;
         if (fallY < 1) {
            return;
         }

         BlockType type = cache.getType(x, fallY, z);
         // Water breaks a fall from any height, so it is always a legal landing.
         if (type == BlockType.WATER) {
            sink.accept(x, fallY, z, costs.getSwim() + costs.getFallPerBlock() * (double)drop, MovementType.DESCEND);
            return;
         }

         if (!type.isPassable()) {
            return;
         }

         if (cache.getType(x, fallY - 1, z).isStandable()) {
            sink.accept(x, fallY, z, costs.getWalk() + costs.getFallPerBlock() * (double)drop, MovementType.DESCEND);
            return;
         }
      }
   }

   /**
    * Diagonal steps, with corner cutting explicitly forbidden: both orthogonal neighbours must be
    * open. Without that check the planner happily squeezes the player through the seam between two
    * diagonally touching blocks, which the collision system does not actually allow.
    */
   private static void generateDiagonal(BlockCache cache, int x, int y, int z, MovementCosts costs, MovementSink sink) {
      for(int i = 0; i < CARDINALS.length; ++i) {
         int[] first = CARDINALS[i];
         int[] second = CARDINALS[(i + 1) % CARDINALS.length];
         if (hasClearance(cache, x + first[0], y, z + first[1]) && hasClearance(cache, x + second[0], y, z + second[1])) {
            int dx = x + first[0] + second[0];
            int dz = z + first[1] + second[1];
            if (canStand(cache, dx, y, dz)) {
               emitGroundMove(cache, sink, dx, y, dz, costs, costs.getWalk() * SQRT_2);
            }
         }
      }
   }

   /** Vertical movement inside water. Horizontal swimming is already covered by the ground rules. */
   private static void generateSwim(BlockCache cache, int x, int y, int z, MovementCosts costs, MovementSink sink) {
      if (cache.getType(x, y + 1, z) == BlockType.WATER) {
         sink.accept(x, y + 1, z, costs.getSwim(), MovementType.SWIM);
      }

      if (cache.getType(x, y - 1, z) == BlockType.WATER) {
         sink.accept(x, y - 1, z, costs.getSwim(), MovementType.SWIM);
      }
   }

   /** Up and down a ladder or vine. Stepping onto one from the side is an ordinary ground move. */
   private static void generateClimb(BlockCache cache, int x, int y, int z, MovementCosts costs, MovementSink sink) {
      BlockType above = cache.getType(x, y + 1, z);
      // Either continue up the column, or top out onto solid ground at the head of the ladder.
      if (above == BlockType.CLIMBABLE || canStand(cache, x, y + 1, z)) {
         sink.accept(x, y + 1, z, costs.getClimb(), MovementType.CLIMB);
      }

      if (cache.getType(x, y - 1, z) == BlockType.CLIMBABLE) {
         sink.accept(x, y - 1, z, costs.getClimb(), MovementType.CLIMB);
      }
   }

   /**
    * Running jumps over genuine gaps.
    *
    * Every intermediate block must be both open AND floorless. Requiring the absence of a floor is
    * what stops the planner from emitting a parkour jump over ground it could simply have walked
    * across - walking is cheaper, so such a jump would never be chosen anyway, but generating it
    * wastes expansions on every node.
    */
   private static void generateParkour(BlockCache cache, int x, int y, int z, MovementCosts costs, MovementSink sink) {
      for(int[] direction : CARDINALS) {
         for(int distance = 2; distance <= costs.getMaxParkour(); ++distance) {
            boolean gapClear = true;

            for(int step = 1; step < distance; ++step) {
               int gx = x + direction[0] * step;
               int gz = z + direction[1] * step;
               if (!hasClearance(cache, gx, y, gz) || cache.getType(gx, y - 1, gz).isStandable()) {
                  gapClear = false;
                  break;
               }
            }

            // Once the run is interrupted, no longer jump in this direction can work either.
            if (!gapClear) {
               break;
            }

            int landX = x + direction[0] * distance;
            int landZ = z + direction[1] * distance;
            if (canStand(cache, landX, y, landZ)) {
               sink.accept(landX, y, landZ, costs.getParkour() * (double)distance, MovementType.PARKOUR);
            }
         }
      }
   }

   /** Emits a flat move as a swim or a walk depending on what the destination block is made of. */
   private static void emitGroundMove(BlockCache cache, MovementSink sink, int x, int y, int z, MovementCosts costs, double baseCost) {
      if (cache.getType(x, y, z) == BlockType.WATER) {
         if (costs.isSwimAllowed()) {
            sink.accept(x, y, z, costs.getSwim(), MovementType.SWIM);
         }
      } else {
         sink.accept(x, y, z, baseCost * penalty(cache, x, y, z, costs), MovementType.WALK);
      }
   }

   /** Extra cost for entering unpleasant but passable blocks, currently cobwebs. */
   private static double penalty(BlockCache cache, int x, int y, int z, MovementCosts costs) {
      return cache.getType(x, y, z) == BlockType.AVOID ? costs.getAvoidMultiplier() : 1.0;
   }

   /** Whether the player's two-block body fits here. */
   private static boolean hasClearance(BlockCache cache, int x, int y, int z) {
      return cache.getType(x, y, z).isPassable() && cache.getType(x, y + 1, z).isPassable();
   }

   /** Whether the player can hold this position: solid ground beneath, or water/ladder around them. */
   private static boolean canStand(BlockCache cache, int x, int y, int z) {
      if (!hasClearance(cache, x, y, z)) {
         return false;
      } else {
         return cache.getType(x, y - 1, z).isStandable() || cache.getType(x, y, z).isSupporting();
      }
   }
}
