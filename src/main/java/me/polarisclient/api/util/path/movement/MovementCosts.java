package me.polarisclient.api.util.path.movement;

/**
 * The tunable weights that decide what the pathfinder considers "cheap".
 *
 * This is deliberately immutable. It is built on the main thread from the module's settings and
 * then handed to the planning thread, so making it read-only removes a whole class of race: the
 * search can never observe half of a settings change.
 *
 * Costs are in arbitrary units where walking one block is 1.0 by default. Only the ratios matter.
 * Raising {@link #getParkour()} relative to {@link #getWalk()}, for instance, makes the planner
 * prefer walking around a gap rather than jumping it.
 */
public final class MovementCosts {
   private final double walk;
   private final double jump;
   private final double fallPerBlock;
   private final double swim;
   private final double climb;
   private final double parkour;
   private final double waterMultiplier;
   private final double avoidMultiplier;
   private final int maxFall;
   private final int maxParkour;
   private final boolean allowDiagonal;
   private final boolean allowParkour;
   private final boolean allowSwim;
   private final boolean allowClimb;
   private final double heuristicWeight;

   private MovementCosts(MovementCosts.Builder builder) {
      this.walk = builder.walk;
      this.jump = builder.jump;
      this.fallPerBlock = builder.fallPerBlock;
      this.swim = builder.swim;
      this.climb = builder.climb;
      this.parkour = builder.parkour;
      this.waterMultiplier = builder.waterMultiplier;
      this.avoidMultiplier = builder.avoidMultiplier;
      this.maxFall = builder.maxFall;
      this.maxParkour = builder.maxParkour;
      this.allowDiagonal = builder.allowDiagonal;
      this.allowParkour = builder.allowParkour;
      this.allowSwim = builder.allowSwim;
      this.allowClimb = builder.allowClimb;
      this.heuristicWeight = builder.heuristicWeight;
   }

   public static MovementCosts.Builder builder() {
      return new MovementCosts.Builder();
   }

   /** Default weights, tuned so ordinary walking wins unless the terrain really calls for something else. */
   public static MovementCosts defaults() {
      return builder().build();
   }

   public double getWalk() {
      return this.walk;
   }

   public double getJump() {
      return this.jump;
   }

   public double getFallPerBlock() {
      return this.fallPerBlock;
   }

   public double getSwim() {
      return this.swim;
   }

   public double getClimb() {
      return this.climb;
   }

   public double getParkour() {
      return this.parkour;
   }

   public double getWaterMultiplier() {
      return this.waterMultiplier;
   }

   public double getAvoidMultiplier() {
      return this.avoidMultiplier;
   }

   public int getMaxFall() {
      return this.maxFall;
   }

   public int getMaxParkour() {
      return this.maxParkour;
   }

   public boolean isDiagonalAllowed() {
      return this.allowDiagonal;
   }

   public boolean isParkourAllowed() {
      return this.allowParkour;
   }

   public boolean isSwimAllowed() {
      return this.allowSwim;
   }

   public boolean isClimbAllowed() {
      return this.allowClimb;
   }

   /**
    * Multiplier applied to the goal's block-distance estimate to turn it into a cost estimate.
    *
    * At weight 1.0 this is the walk cost, which is admissible for horizontal travel - so A* returns
    * a genuinely optimal path there. It is mildly optimistic across cheap falls, where the true cost
    * of dropping a block is below the walk cost; in practice that costs nothing but a few extra
    * expanded nodes. Pushing the weight above 1.0 deliberately over-estimates, which makes the
    * search noticeably faster and the route slightly worse - the usual weighted-A* trade.
    */
   public double getHeuristicScale() {
      return this.walk * this.heuristicWeight;
   }

   public static final class Builder {
      private double walk = 1.0;
      private double jump = 0.6;
      private double fallPerBlock = 0.4;
      private double swim = 2.0;
      private double climb = 1.8;
      private double parkour = 1.5;
      private double waterMultiplier = 2.0;
      private double avoidMultiplier = 8.0;
      private int maxFall = 3;
      private int maxParkour = 4;
      private boolean allowDiagonal = true;
      private boolean allowParkour = false;
      private boolean allowSwim = true;
      private boolean allowClimb = true;
      private double heuristicWeight = 1.0;

      public MovementCosts.Builder walk(double walk) {
         this.walk = walk;
         return this;
      }

      public MovementCosts.Builder jump(double jump) {
         this.jump = jump;
         return this;
      }

      public MovementCosts.Builder fallPerBlock(double fallPerBlock) {
         this.fallPerBlock = fallPerBlock;
         return this;
      }

      public MovementCosts.Builder swim(double swim) {
         this.swim = swim;
         return this;
      }

      public MovementCosts.Builder climb(double climb) {
         this.climb = climb;
         return this;
      }

      public MovementCosts.Builder parkour(double parkour) {
         this.parkour = parkour;
         return this;
      }

      public MovementCosts.Builder waterMultiplier(double waterMultiplier) {
         this.waterMultiplier = waterMultiplier;
         return this;
      }

      public MovementCosts.Builder avoidMultiplier(double avoidMultiplier) {
         this.avoidMultiplier = avoidMultiplier;
         return this;
      }

      public MovementCosts.Builder maxFall(int maxFall) {
         this.maxFall = maxFall;
         return this;
      }

      public MovementCosts.Builder maxParkour(int maxParkour) {
         this.maxParkour = maxParkour;
         return this;
      }

      public MovementCosts.Builder allowDiagonal(boolean allowDiagonal) {
         this.allowDiagonal = allowDiagonal;
         return this;
      }

      public MovementCosts.Builder allowParkour(boolean allowParkour) {
         this.allowParkour = allowParkour;
         return this;
      }

      public MovementCosts.Builder allowSwim(boolean allowSwim) {
         this.allowSwim = allowSwim;
         return this;
      }

      public MovementCosts.Builder allowClimb(boolean allowClimb) {
         this.allowClimb = allowClimb;
         return this;
      }

      public MovementCosts.Builder heuristicWeight(double heuristicWeight) {
         this.heuristicWeight = heuristicWeight;
         return this;
      }

      public MovementCosts build() {
         return new MovementCosts(this);
      }
   }
}
