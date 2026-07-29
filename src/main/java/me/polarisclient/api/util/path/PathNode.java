package me.polarisclient.api.util.path;

import net.minecraft.util.math.BlockPos;

public class PathNode implements Comparable<PathNode> {
   public final BlockPos pos;
   public final double hCost;
   public PathNode parent;
   public double gCost;

   public PathNode(BlockPos pos, PathNode parent, double gCost, double hCost) {
      this.pos = pos;
      this.parent = parent;
      this.gCost = gCost;
      this.hCost = hCost;
   }

   public double fCost() {
      return this.gCost + this.hCost;
   }

   @Override
   public int compareTo(PathNode other) {
      return Double.compare(this.fCost(), other.fCost());
   }
}
