package me.polarisclient.api.util.path.goal;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Follow a moving entity, stopping once within {@code radius} of it.
 *
 * The entity's position is snapshotted rather than read live. The planning thread cannot safely
 * touch an Entity - its position fields are mutated by the main thread every tick, and a non-atomic
 * double read can tear - so {@link #refresh()} copies the position into volatile ints on the main
 * thread and the search reads only those. The snapshot also gives the search a stationary target,
 * which matters: a goal that drifts mid-search breaks A*'s consistency assumption and can send it
 * chasing its own tail.
 */
public class GoalEntity implements Goal {
   private final Entity entity;
   private final double radius;
   private final double radiusSq;
   private volatile int x;
   private volatile int y;
   private volatile int z;

   public GoalEntity(Entity entity, double radius) {
      this.entity = entity;
      this.radius = Math.max(0.0, radius);
      this.radiusSq = this.radius * this.radius;
      this.refresh();
   }

   /** Re-reads the entity's position. MAIN THREAD ONLY, and before submitting a calculation. */
   public void refresh() {
      if (this.entity != null) {
         this.x = MathHelper.floor(this.entity.posX);
         this.y = MathHelper.floor(this.entity.posY);
         this.z = MathHelper.floor(this.entity.posZ);
      }
   }

   public Entity getEntity() {
      return this.entity;
   }

   /** Whether the target is still worth following. */
   public boolean isValid() {
      return this.entity != null && !this.entity.isDead;
   }

   @Override
   public boolean isFinished(int x, int y, int z) {
      double dx = (double)(x - this.x);
      double dy = (double)(y - this.y);
      double dz = (double)(z - this.z);
      return dx * dx + dy * dy + dz * dz <= this.radiusSq;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      double distance = Goal.octile(x - this.x, z - this.z) + (double)Math.abs(y - this.y);
      return Math.max(0.0, distance - this.radius);
   }

   @Override
   public BlockPos getRenderPos() {
      return new BlockPos(this.x, this.y, this.z);
   }

   @Override
   public String toString() {
      return this.entity == null ? "entity" : this.entity.getName();
   }
}
