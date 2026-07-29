package me.polarisclient.api.util.path.movement;

import java.awt.Color;

/**
 * The kinds of step the player can take between two adjacent path nodes.
 *
 * The planner works purely in these types - it never builds {@link Movement} objects while
 * searching, only records which type got it to each node. The concrete objects are constructed once,
 * during path reconstruction, by {@link Movement#create}.
 */
public enum MovementType {
   /** Flat travel, including diagonals. Sprints when the run is long enough to be worth it. */
   WALK(new Color(90, 190, 255)),
   /** Step or jump up one block. */
   ASCEND(new Color(120, 255, 140)),
   /** Drop down one or more blocks. */
   DESCEND(new Color(255, 200, 90)),
   /** Move through water, including vertically. */
   SWIM(new Color(80, 140, 255)),
   /** Move up or down a ladder or vine. */
   CLIMB(new Color(200, 140, 255)),
   /** Running jump across a gap. */
   PARKOUR(new Color(255, 110, 110));

   private final Color debugColor;

   private MovementType(Color debugColor) {
      this.debugColor = debugColor;
   }

   /** Colour used when drawing this segment in the debug renderer. */
   public Color getDebugColor() {
      return this.debugColor;
   }
}
