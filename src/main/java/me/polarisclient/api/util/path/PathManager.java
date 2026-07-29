package me.polarisclient.api.util.path;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import me.polarisclient.api.util.Timer;
import me.polarisclient.api.util.Wrapper;
import me.polarisclient.api.util.path.calc.CalculationResult;
import me.polarisclient.api.util.path.calc.PathCalculator;
import me.polarisclient.api.util.path.goal.Goal;
import me.polarisclient.api.util.path.goal.GoalEntity;
import me.polarisclient.api.util.path.movement.MovementCosts;
import me.polarisclient.api.util.path.world.BlockCache;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

/**
 * Owns the goal, the worker thread, the terrain cache and the current path, and decides when to
 * re-plan. Everything else in the system is a pure component; this is the part with a life cycle.
 *
 * THE THREADING CONTRACT, which the rest of the design depends on:
 *
 *   - Searches run on one background thread. A long search on the main thread is a visible freeze,
 *     and pathfinding is exactly the workload that occasionally goes long.
 *   - That thread touches ONLY a {@link BlockCache} and an immutable {@link MovementCosts}. It never
 *     sees the World, the player, or a Minecraft object of any kind. Minecraft's world is not
 *     thread-safe, and an off-thread read races with block updates arriving on the network thread.
 *   - The cache is filled here, on the main thread, a couple of chunks per tick.
 *   - Results come back through a single Future, polled on the main thread.
 *
 * So there is exactly one shared mutable object between the threads - the cache - and it is written
 * only by replacing whole immutable chunk arrays. No locks are needed anywhere.
 */
public class PathManager implements Wrapper {
   /**
    * Failed searches tolerated before giving up. A cold cache legitimately fails the first few
    * attempts while chunks are still being captured, so failing instantly would make the pathfinder
    * refuse to start whenever the player has just logged in or teleported.
    */
   private static final int MAX_CONSECUTIVE_FAILURES = 6;

   private final BlockCache cache = new BlockCache();
   private final RotationController rotation = new RotationController();
   private final Timer entityRepathTimer = new Timer();

   private ExecutorService worker;
   private Future<CalculationResult> pending;
   private Goal goal;
   private Path path;
   private PathExecutor executor;
   private CalculationResult lastResult;
   private String status = "Idle";
   private int consecutiveFailures;

   private MovementCosts costs = MovementCosts.defaults();
   private int maxNodes = 15000;
   private long timeoutMs = 40L;
   private int cacheRadius = 6;
   private int chunksPerTick = 2;
   private long cacheRefreshMs = 15000L;
   private long entityRepathMs = 1000L;
   private boolean sprintAllowed = true;
   private boolean debug;

   // ---- lifecycle ----

   public void setGoal(Goal goal) {
      this.goal = goal;
      this.discardPath();
      this.consecutiveFailures = 0;
      this.setStatus(goal == null ? "Idle" : "Planning");
   }

   public void stop() {
      this.goal = null;
      this.discardPath();
      if (this.pending != null) {
         this.pending.cancel(true);
         this.pending = null;
      }

      if (this.worker != null) {
         this.worker.shutdownNow();
         this.worker = null;
      }

      this.rotation.clearTarget();
      this.setStatus("Idle");
   }

   /** Drops the current path but keeps the goal, forcing a fresh search next tick. */
   private void discardPath() {
      this.path = null;
      this.executor = null;
   }

   // ---- main thread tick ----

   public void onTick() {
      if (mc.player != null && mc.world != null) {
         this.updateCache();
         this.pollPending();
         if (this.goal != null) {
            if (this.goal instanceof GoalEntity && !((GoalEntity)this.goal).isValid()) {
               // Order matters: setGoal resets the status, so the outcome is recorded after it.
               this.setGoal(null);
               this.setStatus("Target lost");
            } else {
               BlockPos feet = PathExecutor.feetOf();
               if (this.goal.isFinished(feet.getX(), feet.getY(), feet.getZ())) {
                  this.setGoal(null);
                  this.setStatus("Arrived");
               } else if (this.pending == null && this.needsCalculation()) {
                  this.submit(feet);
               }
            }
         }
      }
   }

   /** Called from the input event so movement is written at the point in the tick vanilla expects. */
   public void applyInput(MovementInput input) {
      if (this.goal != null && this.executor != null && this.executor.getState() == PathExecutor.State.RUNNING) {
         this.executor.tick(input, this.sprintAllowed);
      }
   }

   private boolean needsCalculation() {
      if (this.executor == null) {
         return true;
      } else if (this.executor.getState() != PathExecutor.State.RUNNING) {
         // COMPLETE on a partial path means the segment ran out; FAILED means blocked or stuck.
         return true;
      } else if (this.path != null && this.path.isPartial() && this.executor.isNearEnd()) {
         // Plan the next segment before the current one runs out, so the player never stalls.
         return true;
      } else {
         return this.goal instanceof GoalEntity && this.entityRepathTimer.passedMs(this.entityRepathMs);
      }
   }

   private void submit(BlockPos start) {
      if (this.worker == null) {
         this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Polaris-Pathfinder");
            // Daemon so a search in flight can never keep the game from exiting.
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
         });
      }

      // Snapshot the entity's position on THIS thread; the search must see a stationary target.
      if (this.goal instanceof GoalEntity) {
         ((GoalEntity)this.goal).refresh();
      }

      this.entityRepathTimer.reset();
      final BlockCache searchCache = this.cache;
      final Goal searchGoal = this.goal;
      final MovementCosts searchCosts = this.costs;
      final int nodes = this.maxNodes;
      final long timeout = this.timeoutMs;
      final boolean collectDebug = this.debug;
      this.pending = this.worker.submit((Callable<CalculationResult>)() -> PathCalculator.calculate(
            searchCache, start, searchGoal, searchCosts, nodes, timeout, collectDebug
         ));
   }

   private void pollPending() {
      if (this.pending != null && this.pending.isDone()) {
         CalculationResult result = null;

         try {
            result = this.pending.get();
         } catch (Exception var3) {
            // Interrupted or the search threw. Either way it is just a failed attempt.
         }

         this.pending = null;
         if (result == null) {
            this.registerFailure();
         } else {
            this.lastResult = result;
            if (result.isUsable()) {
               this.path = PathOptimizer.optimize(result.getPath());
               this.executor = new PathExecutor(this.path, this.rotation);
               this.consecutiveFailures = 0;
               this.setStatus(
                  (result.getStatus() == CalculationResult.Status.PARTIAL ? "Partial " : "Path ")
                     + this.path.size()
                     + " steps, "
                     + result.getNodesExpanded()
                     + " nodes, "
                     + result.getDurationMs()
                     + "ms"
               );
            } else {
               this.registerFailure();
            }
         }
      }
   }

   private void registerFailure() {
      this.discardPath();
      if (++this.consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
         this.setGoal(null);
         this.setStatus("No path found");
      } else {
         this.setStatus("Searching (" + this.consecutiveFailures + ")");
      }
   }

   /**
    * Captures nearby chunks into the terrain snapshot, a few per tick.
    *
    * Chunks are visited in expanding rings so the ground the player is standing on is always
    * captured first. The per-tick budget is what keeps this invisible: capturing a chunk costs a
    * couple of milliseconds, and doing the whole radius at once would be a noticeable hitch.
    */
   private void updateCache() {
      int centreX = mc.player.chunkCoordX;
      int centreZ = mc.player.chunkCoordZ;
      this.cache.evictOutside(centreX, centreZ, this.cacheRadius + 1);
      int budget = this.chunksPerTick;

      for(int ring = 0; ring <= this.cacheRadius && budget > 0; ++ring) {
         for(int dx = -ring; dx <= ring && budget > 0; ++dx) {
            for(int dz = -ring; dz <= ring && budget > 0; ++dz) {
               // Only the perimeter of this ring; the interior was handled by earlier rings.
               if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                  int chunkX = centreX + dx;
                  int chunkZ = centreZ + dz;
                  if (!this.cache.isCached(chunkX, chunkZ) || this.cache.getAge(chunkX, chunkZ) >= this.cacheRefreshMs) {
                     Chunk chunk = mc.world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
                     if (chunk != null) {
                        this.cache.captureChunk(mc.world, chunk);
                        --budget;
                     }
                  }
               }
            }
         }
      }
   }

   // ---- accessors ----

   public Goal getGoal() {
      return this.goal;
   }

   public Path getPath() {
      return this.path;
   }

   public PathExecutor getExecutor() {
      return this.executor;
   }

   public CalculationResult getLastResult() {
      return this.lastResult;
   }

   public BlockCache getCache() {
      return this.cache;
   }

   public RotationController getRotation() {
      return this.rotation;
   }

   public String getStatus() {
      return this.status;
   }

   private void setStatus(String status) {
      this.status = status;
   }

   public boolean isCalculating() {
      return this.pending != null;
   }

   // ---- configuration, pushed in from the module's settings ----

   public void setCosts(MovementCosts costs) {
      this.costs = costs;
   }

   public void setMaxNodes(int maxNodes) {
      this.maxNodes = maxNodes;
   }

   public void setTimeoutMs(long timeoutMs) {
      this.timeoutMs = timeoutMs;
   }

   public void setCacheRadius(int cacheRadius) {
      this.cacheRadius = cacheRadius;
   }

   public void setChunksPerTick(int chunksPerTick) {
      this.chunksPerTick = chunksPerTick;
   }

   public void setCacheRefreshMs(long cacheRefreshMs) {
      this.cacheRefreshMs = cacheRefreshMs;
   }

   public void setEntityRepathMs(long entityRepathMs) {
      this.entityRepathMs = entityRepathMs;
   }

   public void setSprintAllowed(boolean sprintAllowed) {
      this.sprintAllowed = sprintAllowed;
   }

   public void setDebug(boolean debug) {
      this.debug = debug;
   }
}
