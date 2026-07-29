package me.polarisclient.api.util.path.calc;

import java.util.Collections;
import java.util.List;
import me.polarisclient.api.util.path.Path;
import net.minecraft.util.math.BlockPos;

/** The outcome of one search, including the diagnostics the debug overlay reports. */
public class CalculationResult {
   private final CalculationResult.Status status;
   private final Path path;
   private final int nodesExpanded;
   private final long durationMs;
   private final List<BlockPos> explored;

   public CalculationResult(CalculationResult.Status status, Path path, int nodesExpanded, long durationMs, List<BlockPos> explored) {
      this.status = status;
      this.path = path;
      this.nodesExpanded = nodesExpanded;
      this.durationMs = durationMs;
      this.explored = explored == null ? Collections.emptyList() : Collections.unmodifiableList(explored);
   }

   public static CalculationResult failed(int nodesExpanded, long durationMs, List<BlockPos> explored) {
      return new CalculationResult(CalculationResult.Status.FAILED, null, nodesExpanded, durationMs, explored);
   }

   public CalculationResult.Status getStatus() {
      return this.status;
   }

   public Path getPath() {
      return this.path;
   }

   public boolean isUsable() {
      return this.path != null && !this.path.isEmpty();
   }

   public int getNodesExpanded() {
      return this.nodesExpanded;
   }

   public long getDurationMs() {
      return this.durationMs;
   }

   /** Positions the search touched. Populated only when debug rendering asked for it. */
   public List<BlockPos> getExplored() {
      return this.explored;
   }

   public enum Status {
      /** Reached the goal. */
      SUCCESS,
      /** Ran out of node or time budget, but made progress toward the goal. */
      PARTIAL,
      /** Could not move at all - walled in, or standing in uncached terrain. */
      FAILED;
   }
}
