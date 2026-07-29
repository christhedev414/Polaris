package me.polarisclient.api.util.path.elytra;

/**
 * Phases of an elytra flight.
 *
 * Flight is a control problem rather than a search problem, so unlike ground movement there is no
 * plan to follow - just a sequence of regimes, each with its own pitch policy. The ordering below is
 * the normal progression; terrain can bounce the flight back from CRUISE to CLIMB at any time.
 */
public enum ElytraState {
   /** On the ground, waiting to launch. */
   GROUNDED,
   /** Airborne and wings deployed, but still too low to head anywhere. */
   TAKEOFF,
   /** Gaining altitude toward cruise height. */
   CLIMB,
   /** At altitude and tracking the target. */
   CRUISE,
   /** Past the target, or high above it, trading altitude for ground closed. */
   DESCEND,
   /** Close to the ground: pitching up hard to bleed speed before touchdown. */
   FLARE,
   /** Touched down. The manager hands back to ground pathfinding from here. */
   LANDED,
   /** No elytra, broken elytra, or unable to launch. */
   FAILED;

   public boolean isFlying() {
      return this == TAKEOFF || this == CLIMB || this == CRUISE || this == DESCEND || this == FLARE;
   }

   public boolean isTerminal() {
      return this == LANDED || this == FAILED;
   }
}
