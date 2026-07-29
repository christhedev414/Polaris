package me.polarisclient.mod.modules.impl.misc;

import java.awt.Color;
import me.polarisclient.api.util.path.NavigationMode;
import me.polarisclient.api.util.path.PathExecutor;
import me.polarisclient.api.util.path.PathManager;
import me.polarisclient.api.util.path.PathRenderer;
import me.polarisclient.api.util.path.RotationController;
import me.polarisclient.api.util.path.calc.CalculationResult;
import me.polarisclient.api.util.path.elytra.ElytraSettings;
import me.polarisclient.api.util.path.goal.Goal;
import me.polarisclient.api.util.path.movement.MovementCosts;
import me.polarisclient.mod.commands.Command;
import me.polarisclient.mod.modules.Category;
import me.polarisclient.mod.modules.Module;
import me.polarisclient.mod.modules.settings.Setting;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The user-facing front end for the pathfinding system.
 *
 * Holds no pathfinding logic of its own. Its entire job is to translate settings into a
 * {@link MovementCosts}, pump {@link PathManager} once per tick, and draw the debug overlay - so the
 * pathfinder stays testable and reusable independently of the module system.
 */
public class Pathfind extends Module {
   public static Pathfind INSTANCE;

   // ---- travel mode ----
   private final Setting<NavigationMode> navigation = this.add(new Setting<>("Travel", NavigationMode.GROUND));

   // ---- elytra ----
   private final Setting<Integer> elytraMinDistance = this.add(
      new Setting<>("ElytraMinDist", 150, 20, 1000, v -> this.navigation.getValue() != NavigationMode.GROUND)
   );
   private final Setting<Integer> elytraCruise = this.add(
      new Setting<>("ElytraCruise", 40, 5, 120, v -> this.navigation.getValue() != NavigationMode.GROUND)
   );
   private final Setting<Boolean> elytraFireworks = this.add(
      new Setting<>("Fireworks", true, v -> this.navigation.getValue() != NavigationMode.GROUND)
   );
   private final Setting<Float> elytraClimbPitch = this.add(
      new Setting<>("ClimbPitch", 30.0F, 5.0F, 60.0F, v -> this.navigation.getValue() != NavigationMode.GROUND)
   );
   private final Setting<Float> elytraDivePitch = this.add(
      new Setting<>("DivePitch", 40.0F, 5.0F, 80.0F, v -> this.navigation.getValue() != NavigationMode.GROUND)
   );
   private final Setting<Integer> elytraLandDistance = this.add(
      new Setting<>("LandDist", 48, 10, 200, v -> this.navigation.getValue() != NavigationMode.GROUND)
   );
   private final Setting<Integer> elytraLookAhead = this.add(
      new Setting<>("LookAhead", 24, 8, 64, v -> this.navigation.getValue() != NavigationMode.GROUND)
   );

   // ---- capability toggles ----
   private final Setting<Boolean> sprint = this.add(new Setting<>("Sprint", true));
   private final Setting<Boolean> diagonal = this.add(new Setting<>("Diagonal", true));
   private final Setting<Boolean> swim = this.add(new Setting<>("Swim", true));
   private final Setting<Boolean> climb = this.add(new Setting<>("Climb", true));
   private final Setting<Boolean> parkour = this.add(new Setting<>("Parkour", false));
   private final Setting<Integer> maxFall = this.add(new Setting<>("MaxFall", 3, 1, 20));
   private final Setting<Integer> maxParkour = this.add(new Setting<>("MaxParkour", 4, 2, 5, v -> this.parkour.getValue()));

   // ---- movement costs ----
   private final Setting<Float> walkCost = this.add(new Setting<>("WalkCost", 1.0F, 0.1F, 5.0F));
   private final Setting<Float> jumpCost = this.add(new Setting<>("JumpCost", 0.6F, 0.0F, 5.0F));
   private final Setting<Float> fallCost = this.add(new Setting<>("FallCost", 0.4F, 0.0F, 5.0F));
   private final Setting<Float> swimCost = this.add(new Setting<>("SwimCost", 2.0F, 0.1F, 10.0F));
   private final Setting<Float> climbCost = this.add(new Setting<>("ClimbCost", 1.8F, 0.1F, 10.0F));
   private final Setting<Float> parkourCost = this.add(new Setting<>("ParkourCost", 1.5F, 0.1F, 10.0F, v -> this.parkour.getValue()));

   // ---- search budget ----
   private final Setting<Integer> maxNodes = this.add(new Setting<>("MaxNodes", 15000, 1000, 100000));
   private final Setting<Integer> timeout = this.add(new Setting<>("TimeoutMs", 40, 5, 500));
   private final Setting<Float> heuristicWeight = this.add(new Setting<>("HeuristicWeight", 1.0F, 1.0F, 3.0F));
   private final Setting<Integer> cacheRadius = this.add(new Setting<>("CacheRadius", 6, 2, 12));
   private final Setting<Integer> chunksPerTick = this.add(new Setting<>("ChunksPerTick", 2, 1, 8));

   // ---- rotations ----
   private final Setting<RotationController.Mode> rotationMode = this.add(new Setting<>("Rotations", RotationController.Mode.SMOOTH));
   private final Setting<Float> rotationSpeed = this.add(
      new Setting<>("RotationSpeed", 18.0F, 2.0F, 90.0F, v -> this.rotationMode.getValue() == RotationController.Mode.SMOOTH)
   );

   // ---- debug rendering ----
   private final Setting<Boolean> renderPath = this.add(new Setting<>("RenderPath", true));
   private final Setting<Boolean> renderGoal = this.add(new Setting<>("RenderGoal", true));
   private final Setting<Boolean> renderNodes = this.add(new Setting<>("RenderNodes", false));
   private final Setting<Float> lineWidth = this.add(new Setting<>("LineWidth", 2.0F, 0.5F, 6.0F));
   private final Setting<Float> nodeSize = this.add(new Setting<>("NodeSize", 2.0F, 1.0F, 6.0F, v -> this.renderNodes.getValue()));
   private final Setting<Color> nodeColor = this.add(new Setting<>("NodeColor", new Color(255, 255, 255, 70), v -> this.renderNodes.getValue()));
   private final Setting<Color> goalColor = this.add(new Setting<>("GoalColor", new Color(120, 255, 140), v -> this.renderGoal.getValue()));

   private final PathManager manager = new PathManager();

   public Pathfind() {
      super("Pathfind", "Walks to a goal using A* pathfinding", Category.MISC);
      INSTANCE = this;
   }

   public PathManager getManager() {
      return this.manager;
   }

   /** Entry point used by the goto command: set a destination and start walking. */
   public void pathTo(Goal goal) {
      this.pushSettings();
      this.manager.setGoal(goal);
      if (!this.isOn()) {
         this.enable();
      }
   }

   public void cancel() {
      if (this.isOn()) {
         this.disable();
      } else {
         this.manager.stop();
      }
   }

   @Override
   public void onEnable() {
      this.pushSettings();
      if (this.manager.getGoal() == null && !nullCheck()) {
         this.sendMessage("No goal set. Use " + Command.getCommandPrefix() + "goto <x> <y> <z>.");
      }
   }

   @Override
   public void onDisable() {
      this.manager.stop();
   }

   @Override
   public void onTick() {
      if (!nullCheck()) {
         this.pushSettings();
         this.manager.onTick();
         // The manager clears the goal once it arrives or gives up; mirror that by switching off.
         if (this.manager.getGoal() == null) {
            String status = this.manager.getStatus();
            // "Idle" means the module was switched on without a destination, which onEnable has
            // already explained - no need to say it twice.
            if (!"Idle".equals(status)) {
               this.sendMessage(status);
            }

            this.disable();
         }
      }
   }

   @SubscribeEvent
   public void onInputUpdate(InputUpdateEvent event) {
      if (!nullCheck()) {
         this.manager.applyInput(event.getMovementInput());
      }
   }

   @SubscribeEvent
   public void onRenderWorld(RenderWorldLastEvent event) {
      if (!nullCheck()) {
         if (this.renderNodes.getValue()) {
            CalculationResult result = this.manager.getLastResult();
            if (result != null) {
               PathRenderer.renderExplored(result.getExplored(), this.nodeColor.getValue(), this.nodeSize.getValue());
            }
         }

         if (this.renderPath.getValue()) {
            PathExecutor executor = this.manager.getExecutor();
            PathRenderer.renderPath(this.manager.getPath(), executor == null ? 0 : executor.getIndex(), this.lineWidth.getValue());
         }

         if (this.renderGoal.getValue()) {
            PathRenderer.renderGoal(this.manager.getGoal(), this.goalColor.getValue(), this.lineWidth.getValue());
         }
      }
   }

   /**
    * Rebuilds the immutable cost object and pushes every tunable into the manager.
    *
    * Done every tick rather than only on change: it is a handful of field writes, and it means
    * dragging a slider in the ClickGui takes effect on the next search with no change-tracking
    * machinery to get wrong.
    */
   private void pushSettings() {
      this.manager
         .setCosts(
            MovementCosts.builder()
               .walk(this.walkCost.getValue())
               .jump(this.jumpCost.getValue())
               .fallPerBlock(this.fallCost.getValue())
               .swim(this.swimCost.getValue())
               .climb(this.climbCost.getValue())
               .parkour(this.parkourCost.getValue())
               .maxFall(this.maxFall.getValue())
               .maxParkour(this.maxParkour.getValue())
               .allowDiagonal(this.diagonal.getValue())
               .allowParkour(this.parkour.getValue())
               .allowSwim(this.swim.getValue())
               .allowClimb(this.climb.getValue())
               .heuristicWeight(this.heuristicWeight.getValue())
               .build()
         );
      this.manager.setNavigationMode(this.navigation.getValue());
      this.manager.setElytraMinDistance(this.elytraMinDistance.getValue());
      this.manager
         .setElytraSettings(
            ElytraSettings.builder()
               .cruiseHeight(this.elytraCruise.getValue())
               .useFireworks(this.elytraFireworks.getValue())
               .maxClimbPitch(this.elytraClimbPitch.getValue())
               .maxDivePitch(this.elytraDivePitch.getValue())
               .landingDistance(this.elytraLandDistance.getValue())
               .lookAhead(this.elytraLookAhead.getValue())
               .rotationSpeed(this.rotationSpeed.getValue())
               .build()
         );
      this.manager.setMaxNodes(this.maxNodes.getValue());
      this.manager.setTimeoutMs((long)this.timeout.getValue().intValue());
      this.manager.setCacheRadius(this.cacheRadius.getValue());
      this.manager.setChunksPerTick(this.chunksPerTick.getValue());
      this.manager.setSprintAllowed(this.sprint.getValue());
      this.manager.setDebug(this.renderNodes.getValue());
      this.manager.getRotation().setMode(this.rotationMode.getValue());
      this.manager.getRotation().setSpeed(this.rotationSpeed.getValue());
   }

   @Override
   public String getArrayListInfo() {
      return super.getArrayListInfo() + " " + this.manager.getStatus();
   }
}
