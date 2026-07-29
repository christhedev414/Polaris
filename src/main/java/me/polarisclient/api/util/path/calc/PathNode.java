package me.polarisclient.api.util.path.calc;

import me.polarisclient.api.util.path.movement.MovementType;

/**
 * One block position considered by the search.
 *
 * Fields are public and mutable on purpose. Hundreds of thousands of these are touched per search,
 * and accessors plus defensive copying showed up clearly in profiling; this class is an internal
 * detail of {@link PathCalculator} and is never handed out.
 *
 * Note it stores a {@link MovementType} rather than a Movement. The search only needs to know which
 * KIND of step reached a node; the concrete Movement objects are built once, for the handful of
 * nodes that end up on the final path.
 */
public final class PathNode {
   public final int x;
   public final int y;
   public final int z;
   public final long key;

   /** Cost of the best known route from the start to here. */
   public double gCost = Double.POSITIVE_INFINITY;
   /** Estimated remaining cost to the goal. */
   public double hCost;
   /** gCost + hCost, cached because the heap compares it constantly. */
   public double fCost = Double.POSITIVE_INFINITY;

   public PathNode parent;
   public MovementType movement;

   /** Position in the open-set heap, or -1 when not queued. Enables O(log n) decrease-key. */
   public int heapIndex = -1;
   public boolean closed;

   public PathNode(int x, int y, int z) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.key = key(x, y, z);
   }

   /**
    * Packs a block position into a long: 26 bits of X, 12 of Y, 26 of Z. Y only needs 12 because
    * worlds are 256 tall. This is the map key, and packing it avoids allocating a BlockPos per node.
    */
   public static long key(int x, int y, int z) {
      return ((long)x & 67108863L) << 38 | ((long)y & 4095L) << 26 | (long)z & 67108863L;
   }
}
