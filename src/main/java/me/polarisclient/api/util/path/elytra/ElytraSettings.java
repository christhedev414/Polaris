package me.polarisclient.api.util.path.elytra;

/**
 * Tunables for elytra flight. Immutable, rebuilt from the module's settings each tick, for the same
 * reason {@link me.polarisclient.api.util.path.movement.MovementCosts} is.
 *
 * The pitch numbers are the ones that matter. Minecraft's elytra is a glider with no thrust of its
 * own: pitching down converts altitude into speed, pitching up converts speed into altitude, and
 * holding a steep climb with no speed left simply stalls. The controller therefore treats climb
 * angle as something it has to earn, which is what {@link #getStallSpeed()} governs.
 */
public final class ElytraSettings {
   private final int cruiseHeight;
   private final int terrainClearance;
   private final float maxClimbPitch;
   private final float maxDivePitch;
   private final float pitchGain;
   private final double stallSpeed;
   private final boolean useFireworks;
   private final double boostSpeed;
   private final long boostCooldownMs;
   private final int landingDistance;
   private final int flareHeight;
   private final int lookAhead;
   private final float rotationSpeed;

   private ElytraSettings(ElytraSettings.Builder builder) {
      this.cruiseHeight = builder.cruiseHeight;
      this.terrainClearance = builder.terrainClearance;
      this.maxClimbPitch = builder.maxClimbPitch;
      this.maxDivePitch = builder.maxDivePitch;
      this.pitchGain = builder.pitchGain;
      this.stallSpeed = builder.stallSpeed;
      this.useFireworks = builder.useFireworks;
      this.boostSpeed = builder.boostSpeed;
      this.boostCooldownMs = builder.boostCooldownMs;
      this.landingDistance = builder.landingDistance;
      this.flareHeight = builder.flareHeight;
      this.lookAhead = builder.lookAhead;
      this.rotationSpeed = builder.rotationSpeed;
   }

   public static ElytraSettings.Builder builder() {
      return new ElytraSettings.Builder();
   }

   public static ElytraSettings defaults() {
      return builder().build();
   }

   /** Blocks above the highest terrain ahead to aim for while cruising. */
   public int getCruiseHeight() {
      return this.cruiseHeight;
   }

   /** Minimum vertical gap to terrain before the controller forces a climb. */
   public int getTerrainClearance() {
      return this.terrainClearance;
   }

   public float getMaxClimbPitch() {
      return this.maxClimbPitch;
   }

   public float getMaxDivePitch() {
      return this.maxDivePitch;
   }

   /** Degrees of pitch per block of altitude error. */
   public float getPitchGain() {
      return this.pitchGain;
   }

   /** Horizontal speed below which a steep climb would stall instead of gaining height. */
   public double getStallSpeed() {
      return this.stallSpeed;
   }

   public boolean isUseFireworks() {
      return this.useFireworks;
   }

   /** Fire a rocket when horizontal speed drops below this. */
   public double getBoostSpeed() {
      return this.boostSpeed;
   }

   public long getBoostCooldownMs() {
      return this.boostCooldownMs;
   }

   /** Horizontal distance to the target at which the approach begins. */
   public int getLandingDistance() {
      return this.landingDistance;
   }

   /** Height above ground at which to flare. */
   public int getFlareHeight() {
      return this.flareHeight;
   }

   /** Blocks ahead to probe for terrain. */
   public int getLookAhead() {
      return this.lookAhead;
   }

   /** Maximum degrees of yaw or pitch change per tick. */
   public float getRotationSpeed() {
      return this.rotationSpeed;
   }

   public static final class Builder {
      private int cruiseHeight = 40;
      private int terrainClearance = 12;
      private float maxClimbPitch = 30.0F;
      private float maxDivePitch = 40.0F;
      private float pitchGain = 2.0F;
      private double stallSpeed = 0.35;
      private boolean useFireworks = true;
      private double boostSpeed = 0.55;
      private long boostCooldownMs = 1500L;
      private int landingDistance = 48;
      private int flareHeight = 12;
      private int lookAhead = 24;
      private float rotationSpeed = 12.0F;

      public ElytraSettings.Builder cruiseHeight(int cruiseHeight) {
         this.cruiseHeight = cruiseHeight;
         return this;
      }

      public ElytraSettings.Builder terrainClearance(int terrainClearance) {
         this.terrainClearance = terrainClearance;
         return this;
      }

      public ElytraSettings.Builder maxClimbPitch(float maxClimbPitch) {
         this.maxClimbPitch = maxClimbPitch;
         return this;
      }

      public ElytraSettings.Builder maxDivePitch(float maxDivePitch) {
         this.maxDivePitch = maxDivePitch;
         return this;
      }

      public ElytraSettings.Builder pitchGain(float pitchGain) {
         this.pitchGain = pitchGain;
         return this;
      }

      public ElytraSettings.Builder stallSpeed(double stallSpeed) {
         this.stallSpeed = stallSpeed;
         return this;
      }

      public ElytraSettings.Builder useFireworks(boolean useFireworks) {
         this.useFireworks = useFireworks;
         return this;
      }

      public ElytraSettings.Builder boostSpeed(double boostSpeed) {
         this.boostSpeed = boostSpeed;
         return this;
      }

      public ElytraSettings.Builder boostCooldownMs(long boostCooldownMs) {
         this.boostCooldownMs = boostCooldownMs;
         return this;
      }

      public ElytraSettings.Builder landingDistance(int landingDistance) {
         this.landingDistance = landingDistance;
         return this;
      }

      public ElytraSettings.Builder flareHeight(int flareHeight) {
         this.flareHeight = flareHeight;
         return this;
      }

      public ElytraSettings.Builder lookAhead(int lookAhead) {
         this.lookAhead = lookAhead;
         return this;
      }

      public ElytraSettings.Builder rotationSpeed(float rotationSpeed) {
         this.rotationSpeed = rotationSpeed;
         return this;
      }

      public ElytraSettings build() {
         return new ElytraSettings(this);
      }
   }
}
