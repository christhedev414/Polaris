package me.polarisclient.api.util.path;

/** How the pathfinder should travel to its goal. */
public enum NavigationMode {
   /** Always walk. */
   GROUND,
   /** Fly whenever an elytra is worn and the goal is far enough to be worth launching for. */
   ELYTRA,
   /**
    * Fly the long leg, walk the last stretch. Falls back to walking with no elytra, on a goal that
    * cannot be flown to, or if a launch fails.
    */
   AUTO;
}
