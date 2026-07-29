package me.polarisclient.mod.modules.impl.combat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import me.polarisclient.api.events.impl.PacketEvent;
import me.polarisclient.api.events.impl.Render2DEvent;
import me.polarisclient.api.events.impl.Render3DEvent;
import me.polarisclient.api.events.impl.UpdateWalkingPlayerEvent;
import me.polarisclient.api.managers.Managers;
import me.polarisclient.api.util.DamageUtil;
import me.polarisclient.api.util.InventoryUtil;
import me.polarisclient.api.util.Timer;
import me.polarisclient.api.util.combat.MotionPredictor;
import me.polarisclient.api.util.combat.TerrainSnapshot;
import me.polarisclient.api.util.render.RenderUtil;
import me.polarisclient.mod.modules.Category;
import me.polarisclient.mod.modules.Module;
import me.polarisclient.mod.modules.settings.Setting;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Places a bed next to a target and detonates it.
 *
 * Beds explode when used outside the overworld, with roughly a crystal and a half of power, and
 * unlike a crystal they need no entity and no line of sight to the target - just two free blocks and
 * a floor. That makes them the strongest placement weapon available in the nether and the end, and
 * it is also why this module is so much fussier than a crystal aura: the two-block footprint, the
 * facing derived from player yaw, and the place-then-break cycle all have to line up.
 *
 * Three details drive most of the design:
 *
 *   - A bed's facing is NOT chosen by the placement packet. Vanilla derives it from the placer's
 *     horizontal yaw, so choosing where the head lands means rotating the player first.
 *   - The explosion is power 5.0, not the 6.0 of an end crystal, so the shared crystal damage helper
 *     would over-report every number the min/max damage settings depend on. This computes its own.
 *   - The chosen side flickers badly between ticks when two candidates score within rounding error
 *     of each other, which makes the render strobe and the placement fight itself. A direction lock
 *     damps that.
 */
public class BedAura extends Module {
   public static BedAura INSTANCE;

   /** Bed explosions are power 5.0; the damage curve works in units of twice the power. */
   private static final float BED_EXPLOSION_SIZE = 10.0F;
   private static final float BED_EXPLOSION_POWER = 5.0F;
   /** Damage advantage a direction-locked candidate may give up before the lock is broken. */
   private static final double DIRECTION_LOCK_TOLERANCE = 0.5;
   /** How long the target must sit on the bed before adaptive timing switches to break-then-place. */
   private static final long STUCK_CONFIRM_MS = 1000L;
   /** Ticks without a placement before the module reports itself inactive. */
   private static final int INACTIVE_LIMIT = 10;
   private static final int EXPLOSION_SAMPLES = 20;

   public enum Page {
      GENERAL,
      TIMING,
      BASE_PLACE,
      PREDICTION,
      RENDER;
   }

   public enum RefillMode {
      SLOT_SWAP,
      PICKUP;
   }

   public enum TimingMode {
      INSTANT,
      SWITCH,
      ADAPTIVE;
   }

   /** Stand-in for the ghost-switch override of a Lambda-style HotbarSwitchManager. */
   public enum SwitchBypass {
      NONE,
      SILENT,
      PACKET;
   }

   public enum RenderStyle {
      NORMAL,
      MORPH;
   }

   public enum ColorMode {
      DUAL,
      SINGLE;
   }

   public enum SpawnAnimation {
      STATIC,
      FADE,
      GROW,
      RISE;
   }

   private final Setting<Page> page = this.add(new Setting<>("Page", Page.GENERAL));

   // ---- GENERAL ----
   private final Setting<EnumHand> handMode = this.add(new Setting<>("HandMode", EnumHand.OFF_HAND, v -> this.page.getValue() == Page.GENERAL));
   private final Setting<Integer> rotationPitch = this.add(new Setting<>("RotationPitch", 90, -90, 90, v -> this.page.getValue() == Page.GENERAL));
   private final Setting<SwitchBypass> ghostSwitchBypass = this.add(
      new Setting<>("GhostSwitchBypass", SwitchBypass.NONE, v -> this.page.getValue() == Page.GENERAL && this.isMainHand())
   );
   private final Setting<Integer> bedSlot = this.add(
      new Setting<>("BedSlot", 3, 1, 9, v -> this.page.getValue() == Page.GENERAL && this.isMainHand())
   );
   private final Setting<RefillMode> refillMode = this.add(
      new Setting<>("RefillMode", RefillMode.SLOT_SWAP, v -> this.page.getValue() == Page.GENERAL && this.isMainHand())
   );
   private final Setting<Integer> refillDelay = this.add(new Setting<>("RefillDelay", 40, 0, 500, v -> this.page.getValue() == Page.GENERAL));
   private final Setting<Integer> refillPostDelay = this.add(new Setting<>("RefillPostDelay", 100, 0, 500, v -> this.page.getValue() == Page.GENERAL));
   private final Setting<Boolean> assumeInstantMine = this.add(new Setting<>("AssumeInstantMine", true, v -> this.page.getValue() == Page.GENERAL));
   private final Setting<Boolean> strictDirection = this.add(new Setting<>("StrictDirection", false, v -> this.page.getValue() == Page.GENERAL));
   private final Setting<Boolean> newPlacement = this.add(new Setting<>("1.13Placement", false, v -> this.page.getValue() == Page.GENERAL));
   private final Setting<Float> noSuicide = this.add(new Setting<>("NoSuicide", 8.0F, 0.0F, 20.0F, v -> this.page.getValue() == Page.GENERAL));
   private final Setting<Float> minDamage = this.add(new Setting<>("MinDamage", 6.0F, 0.0F, 20.0F, v -> this.page.getValue() == Page.GENERAL));
   private final Setting<Float> maxSelfDamage = this.add(new Setting<>("MaxSelfDamage", 6.0F, 0.0F, 20.0F, v -> this.page.getValue() == Page.GENERAL));
   private final Setting<Float> damageBalance = this.add(new Setting<>("DamageBalance", -2.5F, -10.0F, 10.0F, v -> this.page.getValue() == Page.GENERAL));
   private final Setting<Float> range = this.add(new Setting<>("Range", 5.4F, 0.0F, 6.0F, v -> this.page.getValue() == Page.GENERAL));

   // ---- BASE_PLACE ----
   private final Setting<Boolean> basePlace = this.add(new Setting<>("BasePlace", false, v -> this.page.getValue() == Page.BASE_PLACE));
   private final Setting<Float> basePlaceMinDamage = this.add(
      new Setting<>("BasePlaceMinDamage", 8.0F, 0.0F, 20.0F, v -> this.page.getValue() == Page.BASE_PLACE && this.basePlace.getValue())
   );
   private final Setting<Integer> basePlaceDelay = this.add(
      new Setting<>("BasePlaceDelay", 0, 0, 1000, v -> this.page.getValue() == Page.BASE_PLACE && this.basePlace.getValue())
   );
   private final Setting<Float> basePlaceMaxY = this.add(
      new Setting<>("BasePlaceMaxY", 1.0F, 0.0F, 10.0F, v -> this.page.getValue() == Page.BASE_PLACE && this.basePlace.getValue())
   );
   private final Setting<Float> basePlaceMaxSpeed = this.add(
      new Setting<>("BasePlaceMaxSpeed", 10.0F, 0.0F, 20.0F, v -> this.page.getValue() == Page.BASE_PLACE && this.basePlace.getValue())
   );
   private final Setting<Float> basePlaceToggleDamage = this.add(
      new Setting<>("BasePlaceToggleDamage", 8.0F, 0.0F, 20.0F, v -> this.page.getValue() == Page.BASE_PLACE && this.basePlace.getValue())
   );
   private final Setting<Float> basePlaceRange = this.add(
      new Setting<>("BasePlaceRange", 4.5F, 0.0F, 6.0F, v -> this.page.getValue() == Page.BASE_PLACE && this.basePlace.getValue())
   );

   // ---- TIMING ----
   private final Setting<Integer> updateDelay = this.add(new Setting<>("UpdateDelay", 50, 5, 500, v -> this.page.getValue() == Page.TIMING));
   private final Setting<TimingMode> timingMode = this.add(new Setting<>("TimingMode", TimingMode.INSTANT, v -> this.page.getValue() == Page.TIMING));
   private final Setting<Integer> delay = this.add(new Setting<>("Delay", 75, 0, 1000, v -> this.page.getValue() == Page.TIMING));
   private final Setting<Integer> placeDelay = this.add(new Setting<>("PlaceDelay", 25, 0, 1000, v -> this.page.getValue() == Page.TIMING));
   private final Setting<Integer> breakDelay = this.add(new Setting<>("BreakDelay", 50, 0, 1000, v -> this.page.getValue() == Page.TIMING));
   private final Setting<Integer> breakPlaceDelay = this.add(new Setting<>("BreakPlaceDelay", 25, 0, 1000, v -> this.page.getValue() == Page.TIMING));
   private final Setting<Float> stuckYThreshold = this.add(new Setting<>("StuckYThreshold", 0.3F, 0.0F, 1.0F, v -> this.page.getValue() == Page.TIMING));
   private final Setting<Float> stuckDistance = this.add(new Setting<>("StuckDistance", 2.5F, 0.0F, 10.0F, v -> this.page.getValue() == Page.TIMING));
   private final Setting<Boolean> slowMode = this.add(new Setting<>("SlowMode", true, v -> this.page.getValue() == Page.TIMING));
   private final Setting<Float> slowModeDamage = this.add(
      new Setting<>("SlowModeDamage", 4.0F, 0.0F, 20.0F, v -> this.page.getValue() == Page.TIMING && this.slowMode.getValue())
   );
   private final Setting<Integer> slowDelay = this.add(
      new Setting<>("SlowDelay", 250, 0, 1000, v -> this.page.getValue() == Page.TIMING && this.slowMode.getValue())
   );
   private final Setting<Integer> slowPlaceDelay = this.add(
      new Setting<>("SlowPlaceDelay", 250, 0, 1000, v -> this.page.getValue() == Page.TIMING && this.slowMode.getValue())
   );
   private final Setting<Integer> slowBreakDelay = this.add(
      new Setting<>("SlowBreakDelay", 50, 0, 1000, v -> this.page.getValue() == Page.TIMING && this.slowMode.getValue())
   );

   // ---- PREDICTION ----
   private final Setting<Boolean> extrapolate = this.add(new Setting<>("Extrapolate", true, v -> this.page.getValue() == Page.PREDICTION));
   private final Setting<Boolean> selfExtrapolation = this.add(new Setting<>("SelfExtrapolation", true, v -> this.page.getValue() == Page.PREDICTION));
   private final Setting<Boolean> latencySync = this.add(new Setting<>("LatencySync", false, v -> this.page.getValue() == Page.PREDICTION));
   private final Setting<Integer> baseLookahead = this.add(new Setting<>("BaseLookahead", 2, 0, 20, v -> this.page.getValue() == Page.PREDICTION));
   private final Setting<Integer> lookaheadIncrement = this.add(new Setting<>("LookaheadIncrement", 4, 0, 20, v -> this.page.getValue() == Page.PREDICTION));
   private final Setting<Integer> maxLookahead = this.add(new Setting<>("MaxLookahead", 10, 0, 40, v -> this.page.getValue() == Page.PREDICTION));
   private final Setting<Boolean> verticalExtrapolation = this.add(new Setting<>("VerticalExtrapolation", true, v -> this.page.getValue() == Page.PREDICTION));
   private final Setting<Integer> airborneThreshold = this.add(new Setting<>("AirborneThreshold", 39, 1, 200, v -> this.page.getValue() == Page.PREDICTION));
   private final Setting<Integer> gravityRamp = this.add(new Setting<>("GravityRamp", 2, 1, 6, v -> this.page.getValue() == Page.PREDICTION));
   private final Setting<Integer> fallAcceleration = this.add(new Setting<>("FallAcceleration", 2, 1, 20, v -> this.page.getValue() == Page.PREDICTION));
   private final Setting<Integer> fallCurve = this.add(new Setting<>("FallCurve", 1, 1, 6, v -> this.page.getValue() == Page.PREDICTION));
   private final Setting<Boolean> axisSplit = this.add(new Setting<>("AxisSplit", true, v -> this.page.getValue() == Page.PREDICTION));
   private final Setting<Boolean> holeExitClamp = this.add(new Setting<>("HoleExitClamp", false, v -> this.page.getValue() == Page.PREDICTION));
   private final Setting<Boolean> elevatedHoleClamp = this.add(
      new Setting<>("ElevatedHoleClamp", false, v -> this.page.getValue() == Page.PREDICTION && this.holeExitClamp.getValue())
   );
   private final Setting<Boolean> stepCorrection = this.add(new Setting<>("StepCorrection", false, v -> this.page.getValue() == Page.PREDICTION));
   private final Setting<Integer> maxSteps = this.add(
      new Setting<>("MaxSteps", 2, 0, 5, v -> this.page.getValue() == Page.PREDICTION && this.stepCorrection.getValue())
   );
   private final Setting<Double> stepSpeedThreshold = this.add(
      new Setting<>("StepSpeedThreshold", 0.3, 0.0, 2.0, v -> this.page.getValue() == Page.PREDICTION && this.stepCorrection.getValue())
   );
   private final Setting<Double> directionResetAngle = this.add(new Setting<>("DirectionResetAngle", 15.0, 0.0, 180.0, v -> this.page.getValue() == Page.PREDICTION));

   // ---- RENDER ----
   private final Setting<Boolean> noSwing = this.add(new Setting<>("NoSwing", false, v -> this.page.getValue() == Page.RENDER));
   private final Setting<Boolean> renderDamage = this.add(new Setting<>("RenderDamage", true, v -> this.page.getValue() == Page.RENDER));
   private final Setting<RenderStyle> renderStyle = this.add(new Setting<>("RenderStyle", RenderStyle.NORMAL, v -> this.page.getValue() == Page.RENDER));
   private final Setting<ColorMode> colorMode = this.add(new Setting<>("ColorMode", ColorMode.DUAL, v -> this.page.getValue() == Page.RENDER));
   private final Setting<Color> color = this.add(
      new Setting<>("Color", new Color(255, 160, 64), v -> this.page.getValue() == Page.RENDER && this.colorMode.getValue() == ColorMode.SINGLE)
   );
   private final Setting<Color> footColor = this.add(
      new Setting<>("FootColor", new Color(255, 160, 64), v -> this.page.getValue() == Page.RENDER && this.colorMode.getValue() == ColorMode.DUAL)
   );
   private final Setting<Color> headColor = this.add(
      new Setting<>("HeadColor", new Color(255, 32, 64), v -> this.page.getValue() == Page.RENDER && this.colorMode.getValue() == ColorMode.DUAL)
   );
   private final Setting<Integer> outlineAlpha = this.add(new Setting<>("OutlineAlpha", 233, 0, 255, v -> this.page.getValue() == Page.RENDER));
   private final Setting<Integer> fillAlpha = this.add(new Setting<>("FillAlpha", 32, 0, 255, v -> this.page.getValue() == Page.RENDER));
   private final Setting<Float> lineWidth = this.add(new Setting<>("LineWidth", 2.0F, 0.5F, 6.0F, v -> this.page.getValue() == Page.RENDER));
   private final Setting<Integer> morphSpeed = this.add(
      new Setting<>("MorphSpeed", 200, 0, 2000, v -> this.page.getValue() == Page.RENDER && this.renderStyle.getValue() == RenderStyle.MORPH)
   );
   private final Setting<Integer> rotateLength = this.add(new Setting<>("RotateLength", 250, 0, 2000, v -> this.page.getValue() == Page.RENDER));
   private final Setting<Integer> movingLength = this.add(
      new Setting<>("MovingLength", 500, 0, 2000, v -> this.page.getValue() == Page.RENDER && this.renderStyle.getValue() == RenderStyle.MORPH)
   );
   private final Setting<SpawnAnimation> spawnAnimation = this.add(new Setting<>("SpawnAnimation", SpawnAnimation.FADE, v -> this.page.getValue() == Page.RENDER));
   private final Setting<Integer> fadeLength = this.add(new Setting<>("FadeLength", 300, 0, 2000, v -> this.page.getValue() == Page.RENDER));
   private final Setting<Integer> growLength = this.add(new Setting<>("GrowLength", 250, 0, 2000, v -> this.page.getValue() == Page.RENDER));
   private final Setting<Integer> shrinkLength = this.add(new Setting<>("ShrinkLength", 300, 0, 2000, v -> this.page.getValue() == Page.RENDER));
   private final Setting<Integer> riseLength = this.add(new Setting<>("RiseLength", 250, 0, 2000, v -> this.page.getValue() == Page.RENDER));
   private final Setting<Integer> fallLength = this.add(new Setting<>("FallLength", 300, 0, 2000, v -> this.page.getValue() == Page.RENDER));

   // ---- state ----
   private final MotionPredictor predictor = new MotionPredictor();
   private final BedAura.Renderer renderer = new BedAura.Renderer();
   private final Timer updateTimer = new Timer();
   private final Timer actionTimer = new Timer();
   private final Timer refillTimer = new Timer();
   private final Timer basePlaceTimer = new Timer();
   private final int[] explosionCounts = new int[EXPLOSION_SAMPLES];
   private int explosionIndex;
   private java.util.concurrent.ExecutorService executor;
   private java.util.concurrent.Future<List<BedAura.CalcInfo>> pendingSearch;
   private BedAura.PlaceInfo placeInfo;
   private EntityPlayer target;
   private EnumFacing lockedDirection;
   private boolean needOffhandBed;
   private boolean placeNext = true;
   private long stuckSince;
   private int inactiveTicks = INACTIVE_LIMIT;

   public BedAura() {
      super("BedAura", "Place bed and kills enemies", Category.COMBAT);
      INSTANCE = this;
   }

   private boolean isMainHand() {
      return this.handMode.getValue() == EnumHand.MAIN_HAND;
   }

   /** Whether the module has acted recently enough to count as engaged. */
   public boolean isActive() {
      return this.isOn() && this.inactiveTicks < INACTIVE_LIMIT;
   }

   public boolean needsOffhandBed() {
      return this.needOffhandBed;
   }

   @Override
   public String getArrayListInfo() {
      return super.getArrayListInfo() + " " + this.getHudInfo();
   }

   /** Explosions per second, averaged over the sample window. Twenty ticks per second, so times four. */
   public String getHudInfo() {
      int total = 0;

      for(int count : this.explosionCounts) {
         total += count;
      }

      return String.format("%.1f", (double)total / (double)EXPLOSION_SAMPLES * 4.0);
   }

   @Override
   public void onDisable() {
      this.resetState();
   }

   private void resetState() {
      this.shutdownSearch();
      this.placeInfo = null;
      this.target = null;
      this.lockedDirection = null;
      this.needOffhandBed = false;
      this.placeNext = true;
      this.stuckSince = 0L;
      this.inactiveTicks = INACTIVE_LIMIT;
      this.predictor.clear();
      this.renderer.reset();
   }

   // ---- events ----

   @Override
   public void onUpdate() {
      if (!nullCheck()) {
         ++this.inactiveTicks;
         this.explosionIndex = (this.explosionIndex + 1) % EXPLOSION_SAMPLES;
         this.explosionCounts[this.explosionIndex] = 0;
         this.pushPredictorSettings();
         this.predictor.update((double)this.range.getValue().floatValue() + 8.0);
         this.update();
         this.runLoop();
      }
   }

   /**
    * Aims at the placement while it is pending.
    *
    * Two aiming modes, because the bed's facing comes from the player's yaw rather than from the
    * packet: honour the client's global strict-place rotation by looking at the hit vector, or aim
    * along the placement direction with a fixed pitch. The latter is what actually controls which
    * way the bed's head points.
    */
   @SubscribeEvent
   public void onUpdateWalkingPlayer(UpdateWalkingPlayerEvent event) {
      if (!nullCheck() && this.placeInfo != null) {
         if (CombatSetting.INSTANCE != null && CombatSetting.INSTANCE.strictPlace.getValue()) {
            Managers.ROTATIONS.lookAtVec3dPacket(this.placeInfo.hitVec);
         } else {
            Managers.ROTATIONS.setRotations(this.placeInfo.direction.getHorizontalAngle(), (float)this.rotationPitch.getValue().intValue());
         }
      }
   }

   @SubscribeEvent
   public void onReceivePacket(PacketEvent.Receive event) {
      if (event.getPacket() instanceof SPacketSoundEffect) {
         SPacketSoundEffect packet = (SPacketSoundEffect)event.getPacket();
         if (packet.getSound() == SoundEvents.ENTITY_GENERIC_EXPLODE) {
            this.explosionCounts[this.explosionIndex]++;
         }
      }
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      this.renderer.render3D();
   }

   @Override
   public void onRender2D(Render2DEvent event) {
      this.renderer.render2D();
   }

   // ---- placement search ----

   /**
    * Drives the search pipeline, which is split across two threads at the one seam where that is
    * both safe and worthwhile.
    *
    * The problem: the whole search touches the World, and World cannot be read off the main thread -
    * the network thread mutates chunks as block updates arrive, so a concurrent getBlockState can
    * observe a half-applied change or trip a chunk load. But the search is also the most expensive
    * thing this module does, and the expensive half is the vanilla explosion maths: a density
    * calculation casts around 45 rays, and doing that for both parties across every surviving
    * candidate is thousands of raytraces every update.
    *
    * The split:
    *   1. MAIN - capture a {@link TerrainSnapshot} of the search cube. One pass, no raytracing.
    *   2. WORKER - enumerate every position and facing against the snapshot, discard the ones a bed
    *      cannot legally occupy, and rank what is left by an upper bound on damage. This is the
    *      part that scales with the search volume, and it touches nothing but the snapshot.
    *   3. MAIN - compute exact vanilla damage for the handful of survivors and pick a winner.
    *
    * Step 3 stays on the main thread deliberately. The damage figures are what Min Damage and Max
    * Self Damage are compared against, so they have to be the real vanilla numbers rather than an
    * approximation computed against a simplified occlusion model - a damage predictor that is
    * quietly wrong is worse than one that is slow.
    */
   private void update() {
      if (mc.player.dimension == 0 || !this.hasBedSomewhere()) {
         this.placeInfo = null;
         this.cancelSearch();
      } else {
         this.collectSearchResult();
         if (this.pendingSearch == null && this.updateTimer.passedMs((long)this.updateDelay.getValue().intValue())) {
            this.updateTimer.reset();
            this.target = me.polarisclient.api.util.CombatUtil.getTarget((double)this.range.getValue().floatValue() + 8.0);
            if (this.target == null) {
               this.placeInfo = null;
            } else {
               this.submitSearch(this.target);
            }
         }
      }
   }

   /** Captures the snapshot and hands the enumeration to the worker. Main thread. */
   private void submitSearch(EntityPlayer target) {
      Vec3d eyes = mc.player.getPositionEyes(1.0F);
      Vec3d targetPos = this.extrapolate.getValue()
         ? this.predictor.predict(target, this.predictor.getLookahead(target, this.baseLookahead.getValue()))
         : target.getPositionVector();
      boolean allowBase = this.basePlace.getValue()
         && this.basePlaceTimer.passedMs((long)this.basePlaceDelay.getValue().intValue())
         && this.hasObsidian()
         && this.predictor.getSpeed(target) <= (double)this.basePlaceMaxSpeed.getValue().floatValue();

      float searchRange = this.range.getValue();
      // A little margin past the search radius, so base-place support blocks one below the sphere
      // and the head block one step outward are still inside the snapshot.
      int radius = MathHelper.ceil(searchRange) + 2;
      BlockPos centre = new BlockPos(eyes.x, eyes.y, eyes.z);
      TerrainSnapshot snapshot = TerrainSnapshot.capture(mc.world, centre, radius);

      BedAura.SearchRequest request = new BedAura.SearchRequest(
         snapshot,
         centre,
         eyes,
         targetPos,
         searchRange,
         this.basePlaceRange.getValue(),
         mc.player.posY,
         this.basePlaceMaxY.getValue(),
         this.minDamage.getValue(),
         // Difficulty scaling is read here, on the main thread, so the worker's bound stays a real
         // upper bound on hard difficulty rather than understating by a third.
         DamageUtil.getDamageMultiplied(1.0F),
         allowBase
      );
      this.pendingSearch = this.searchExecutor().submit(request);
   }

   /** Picks up a finished search and turns its shortlist into a committed placement. Main thread. */
   private void collectSearchResult() {
      if (this.pendingSearch != null && this.pendingSearch.isDone()) {
         List<BedAura.CalcInfo> shortlist = null;

         try {
            shortlist = this.pendingSearch.get();
         } catch (Exception var4) {
            // Interrupted or the worker threw; treat it as a search that found nothing.
         }

         this.pendingSearch = null;
         if (shortlist != null && !shortlist.isEmpty() && this.target != null) {
            this.placeInfo = this.scoreShortlist(shortlist, this.target);
         } else {
            this.placeInfo = null;
         }
      }
   }

   /**
    * Prices the shortlist with real vanilla explosion maths and picks a winner.
    *
    * Only ever sees the handful of candidates the worker ranked highest, so the expensive part -
    * roughly 45 rays per damage figure, two figures per candidate - stays bounded regardless of how
    * big the search volume was.
    */
   private BedAura.PlaceInfo scoreShortlist(List<BedAura.CalcInfo> shortlist, EntityPlayer target) {
      Vec3d eyes = mc.player.getPositionEyes(1.0F);
      Vec3d targetPos = this.extrapolate.getValue()
         ? this.predictor.predict(target, this.predictor.getLookahead(target, this.baseLookahead.getValue()))
         : target.getPositionVector();
      Vec3d selfPos = this.selfExtrapolation.getValue()
         ? this.predictor.predict(mc.player, this.predictor.getLookahead(mc.player, this.baseLookahead.getValue()))
         : mc.player.getPositionVector();

      List<BedAura.DamageInfo> priced = new ArrayList<>();
      List<BedAura.DamageInfo> noBase = new ArrayList<>();

      for(BedAura.CalcInfo candidate : shortlist) {
         // The world may have moved on since the snapshot was taken a tick ago, so re-check the
         // two blocks that actually matter against the live world before committing to them.
         BlockPos head = candidate.pos.offset(candidate.side);
         if (this.isReplaceable(candidate.pos) && this.isReplaceable(head)) {
            Vec3d explosion = new Vec3d(
               (double)candidate.pos.getX() + 0.5, (double)candidate.pos.getY() + 0.5, (double)candidate.pos.getZ() + 0.5
            );
            float targetDamage = calculateBedDamage(explosion, target, targetPos);
            float selfDamage = calculateBedDamage(explosion, mc.player, selfPos);
            float minimum = candidate.basePlaceFoot == null && candidate.basePlaceHead == null
               ? this.minDamage.getValue()
               : this.basePlaceMinDamage.getValue();
            if (!(selfDamage > this.maxSelfDamage.getValue())
               && !(mc.player.getHealth() + mc.player.getAbsorptionAmount() - selfDamage < this.noSuicide.getValue())
               && !(targetDamage < minimum)
               && !(targetDamage - selfDamage < this.damageBalance.getValue())) {
               BedAura.DamageInfo info = new BedAura.DamageInfo(
                  candidate, targetDamage, selfDamage, candidate.basePlaceFoot, candidate.basePlaceHead
               );
               priced.add(info);
               if (!info.needsBase()) {
                  noBase.add(info);
               }
            }
         }
      }

      if (priced.isEmpty()) {
         return null;
      } else {
         // A placement needing no support block is strictly better once it already hits hard enough:
         // one fewer packet, and nothing for the target to mine out from under it.
         BedAura.DamageInfo best = this.selectWithDirectionLock(noBase, eyes);
         if (best == null || best.targetDamage < this.basePlaceToggleDamage.getValue()) {
            BedAura.DamageInfo withBase = this.selectWithDirectionLock(priced, eyes);
            if (withBase != null && (best == null || withBase.targetDamage > best.targetDamage)) {
               best = withBase;
            }
         }

         if (best == null) {
            return null;
         } else {
            this.lockedDirection = best.calcInfo.side;
            BlockPos head = best.calcInfo.pos.offset(best.calcInfo.side);
            return new BedAura.PlaceInfo(
               best.calcInfo.pos.down(),
               best.calcInfo.pos,
               head,
               best.calcInfo.side,
               best.calcInfo.hitVec,
               best.targetDamage,
               best.needsBase(),
               best.calcInfo.basePlaceFoot,
               best.calcInfo.basePlaceHead
            );
         }
      }
   }

   private java.util.concurrent.ExecutorService searchExecutor() {
      if (this.executor == null) {
         this.executor = java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Polaris-BedAura");
            // Daemon so an in-flight search can never hold the game open.
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
         });
      }

      return this.executor;
   }

   private void cancelSearch() {
      if (this.pendingSearch != null) {
         this.pendingSearch.cancel(true);
         this.pendingSearch = null;
      }
   }

   private void shutdownSearch() {
      this.cancelSearch();
      if (this.executor != null) {
         this.executor.shutdownNow();
         this.executor = null;
      }
   }

   /**
    * Picks the best candidate, preferring last tick's facing when it is close to optimal.
    *
    * Without this the selection oscillates: two facings that differ by hundredths of a damage point
    * trade places every update, which strobes the render and makes the module re-aim and re-place
    * constantly instead of committing to one side.
    */
   private BedAura.DamageInfo selectWithDirectionLock(List<BedAura.DamageInfo> candidates, Vec3d eyes) {
      if (candidates.isEmpty()) {
         return null;
      } else {
         Comparator<BedAura.DamageInfo> order = Comparator.<BedAura.DamageInfo>comparingDouble(info -> -info.targetDamage)
            .thenComparingDouble(
               info -> eyes.squareDistanceTo(
                  (double)info.calcInfo.pos.getX() + 0.5, (double)info.calcInfo.pos.getY() + 0.5, (double)info.calcInfo.pos.getZ() + 0.5
               )
            );
         candidates.sort(order);
         BedAura.DamageInfo best = candidates.get(0);
         if (this.lockedDirection == null) {
            return best;
         } else {
            for(BedAura.DamageInfo info : candidates) {
               if (info.calcInfo.side == this.lockedDirection) {
                  return (double)(best.targetDamage - info.targetDamage) <= DIRECTION_LOCK_TOLERANCE ? info : best;
               }
            }

            return best;
         }
      }
   }


   // ---- execution ----

   private void runLoop() {
      if (this.placeInfo == null) {
         this.needOffhandBed = false;
      } else {
         if (this.isMainHand()) {
            int slot = this.bedSlot.getValue() - 1;
            if (!this.isBed(mc.player.inventory.getStackInSlot(slot))) {
               this.refillBed(slot);
               return;
            }
         } else {
            this.needOffhandBed = true;
            if (!this.isBed(mc.player.getHeldItemOffhand())) {
               return;
            }
         }

         TimingMode mode = this.resolveTimingMode();
         boolean slow = this.slowMode.getValue() && this.placeInfo.targetDamage < this.slowModeDamage.getValue();
         switch(mode) {
            case INSTANT:
               if (this.actionTimer.passedMs((long)(slow ? this.slowDelay.getValue() : this.delay.getValue()).intValue())) {
                  this.placeBed();
                  this.breakBed();
                  this.actionTimer.reset();
               }
               break;
            case SWITCH:
               // Alternating gives the server a tick to register the placement before the use that
               // detonates it, which some anticheats require and which avoids wasting a bed.
               long switchDelay = (long)(this.placeNext
                     ? (slow ? this.slowPlaceDelay.getValue() : this.placeDelay.getValue())
                     : (slow ? this.slowBreakDelay.getValue() : this.breakDelay.getValue()))
                  .intValue();
               if (this.actionTimer.passedMs(switchDelay)) {
                  if (this.placeNext) {
                     this.placeBed();
                  } else {
                     this.breakBed();
                  }

                  this.placeNext = !this.placeNext;
                  this.actionTimer.reset();
               }
               break;
            case ADAPTIVE:
               if (this.actionTimer.passedMs((long)this.breakPlaceDelay.getValue().intValue())) {
                  // Break first: the target is sitting on a bed that is already there.
                  this.breakBed();
                  this.placeBed();
                  this.actionTimer.reset();
               }
         }
      }
   }

   /**
    * Resolves the effective timing mode.
    *
    * A mining module already chewing on one of the bed blocks means the break is being handled
    * elsewhere, so the aura must not add its own delay on top - it forces instant and lets the
    * miner set the pace.
    */
   private TimingMode resolveTimingMode() {
      if (PacketMine.breakPos != null
         && (PacketMine.breakPos.equals(this.placeInfo.bedPosFoot) || PacketMine.breakPos.equals(this.placeInfo.bedPosHead))) {
         this.stuckSince = 0L;
         return TimingMode.INSTANT;
      } else if (this.timingMode.getValue() != TimingMode.ADAPTIVE) {
         return this.timingMode.getValue();
      } else if (this.target == null) {
         return TimingMode.INSTANT;
      } else if (this.isTargetStuckOnBed(this.target)) {
         if (this.stuckSince == 0L) {
            this.stuckSince = System.currentTimeMillis();
         }

         // Only commit to break-then-place once the target has genuinely settled, otherwise a
         // single frame of contact flips the whole cycle around.
         return System.currentTimeMillis() - this.stuckSince >= STUCK_CONFIRM_MS ? TimingMode.ADAPTIVE : TimingMode.INSTANT;
      } else {
         this.stuckSince = 0L;
         return this.predictor.getSpeed(this.target) > 0.05 ? TimingMode.SWITCH : TimingMode.INSTANT;
      }
   }

   /**
    * Whether the target is standing on the bed rather than moving across it.
    *
    * The fractional-Y test catches a player perched on the bed's slab height; the corner probes
    * catch one standing on the boundary between the two halves, where the block directly below is
    * bed but the entity's centre reads as air.
    */
   private boolean isTargetStuckOnBed(EntityPlayer target) {
      if (this.placeInfo == null) {
         return false;
      } else {
         double footDistance = target.getDistanceSq(this.placeInfo.bedPosFoot);
         double headDistance = target.getDistanceSq(this.placeInfo.bedPosHead);
         double limit = (double)(this.stuckDistance.getValue() * this.stuckDistance.getValue());
         if (!(footDistance > limit) && !(headDistance > limit)) {
            if (target.posY - (double)this.placeInfo.bedPosHead.getY() > 2.0) {
               return false;
            } else {
               double fractionalY = target.posY - (double)MathHelper.floor(target.posY);
               if (fractionalY > (double)this.stuckYThreshold.getValue().floatValue()) {
                  return true;
               } else {
                  for(double offsetX = -0.3; offsetX <= 0.3; offsetX += 0.6) {
                     for(double offsetZ = -0.3; offsetZ <= 0.3; offsetZ += 0.6) {
                        BlockPos probe = new BlockPos(target.posX + offsetX, target.posY - 0.3, target.posZ + offsetZ);
                        net.minecraft.block.Block block = mc.world.getBlockState(probe).getBlock();
                        if (block != Blocks.AIR && !(block instanceof BlockBed)) {
                           return false;
                        }
                     }
                  }

                  return true;
               }
            }
         } else {
            return false;
         }
      }
   }

   private void placeBed() {
      if (this.placeInfo != null) {
         if (this.placeInfo.needsBasePlace) {
            this.placeSupport(this.placeInfo.basePlaceFoot);
            this.placeSupport(this.placeInfo.basePlaceHead);
            this.basePlaceTimer.reset();
         }

         EnumHand hand = this.handMode.getValue();
         int previousSlot = mc.player.inventory.currentItem;
         if (this.isMainHand()) {
            this.ghostSwitch(this.bedSlot.getValue() - 1);
         }

         mc.player.connection
            .sendPacket(
               new CPacketPlayerTryUseItemOnBlock(this.placeInfo.basePos, EnumFacing.UP, hand, 0.5F, 1.0F, 0.5F)
            );
         if (!this.noSwing.getValue()) {
            mc.player.connection.sendPacket(new CPacketAnimation(hand));
         }

         if (this.isMainHand() && this.ghostSwitchBypass.getValue() != SwitchBypass.NONE) {
            InventoryUtil.switchToHotbarSlot(previousSlot, true);
         }

         if (this.target != null) {
            mc.player.setRevengeTarget(this.target);
         }

         this.renderer.onPlaced(this.placeInfo);
         this.inactiveTicks = 0;
      }
   }

   private void placeSupport(BlockPos pos) {
      if (pos != null && this.isReplaceable(pos)) {
         EnumFacing side = this.firstPlaceableSide(pos);
         if (side != null) {
            int obsidian = InventoryUtil.findHotbarBlock(Blocks.OBSIDIAN);
            if (obsidian != -1) {
               this.ghostSwitch(obsidian);
               BlockPos against = pos.offset(side);
               Vec3d hit = new Vec3d((double)against.getX() + 0.5, (double)against.getY() + 0.5, (double)against.getZ() + 0.5);
               mc.player.connection
                  .sendPacket(
                     new CPacketPlayerTryUseItemOnBlock(
                        against,
                        side.getOpposite(),
                        EnumHand.MAIN_HAND,
                        (float)(hit.x - (double)against.getX()),
                        (float)(hit.y - (double)against.getY()),
                        (float)(hit.z - (double)against.getZ())
                     )
                  );
               if (!this.noSwing.getValue()) {
                  mc.player.connection.sendPacket(new CPacketAnimation(EnumHand.MAIN_HAND));
               }
            }
         }
      }
   }

   private void breakBed() {
      if (this.placeInfo != null) {
         EnumFacing side = this.miningSide(this.placeInfo.bedPosFoot);
         Vec3d offset = new Vec3d(side.getDirectionVec());
         mc.player.connection
            .sendPacket(
               new CPacketPlayerTryUseItemOnBlock(
                  this.placeInfo.bedPosFoot,
                  side,
                  this.handMode.getValue(),
                  (float)(0.5 + offset.x * 0.5),
                  (float)(0.5 + offset.y * 0.5),
                  (float)(0.5 + offset.z * 0.5)
               )
            );
         if (!this.noSwing.getValue()) {
            mc.player.connection.sendPacket(new CPacketAnimation(this.handMode.getValue()));
         }

         this.actionTimer.reset();
      }
   }

   /** Pulls a bed out of storage into the configured hotbar slot. */
   private void refillBed(int hotbarSlot) {
      if (this.refillTimer.passedMs((long)this.refillDelay.getValue().intValue())) {
         int source = -1;

         for(int slot = 9; slot < 36; ++slot) {
            if (this.isBed(mc.player.inventory.getStackInSlot(slot))) {
               source = slot;
               break;
            }
         }

         if (source == -1) {
            for(int slot = 1; slot < 5; ++slot) {
               if (this.isBed(mc.player.inventoryContainer.getSlot(slot).getStack())) {
                  source = slot;
                  break;
               }
            }
         }

         if (source != -1) {
            if (this.refillMode.getValue() == RefillMode.SLOT_SWAP) {
               mc.playerController.windowClick(0, source, hotbarSlot, ClickType.SWAP, mc.player);
            } else {
               mc.playerController.windowClick(0, source, 0, ClickType.PICKUP, mc.player);
               mc.playerController.windowClick(0, 36 + hotbarSlot, 0, ClickType.PICKUP, mc.player);
               mc.playerController.windowClick(0, source, 0, ClickType.PICKUP, mc.player);
            }

            this.refillTimer.reset();
            this.actionTimer.setMs((long)this.refillPostDelay.getValue().intValue());
         }
      }
   }

   private void ghostSwitch(int slot) {
      if (slot >= 0 && slot != mc.player.inventory.currentItem) {
         InventoryUtil.switchToHotbarSlot(slot, this.ghostSwitchBypass.getValue() != SwitchBypass.NONE);
      }
   }

   // ---- helpers ----

   /**
    * Bed explosion damage.
    *
    * Distinct from the shared crystal helper because a bed is power 5.0 against a crystal's 6.0.
    * Reusing the crystal maths would overstate every number by roughly a fifth, which silently
    * breaks Min Damage and Max Self Damage - the module would happily blow itself up while
    * reporting that it was inside the limit.
    */
   private static float calculateBedDamage(Vec3d explosion, Entity entity, Vec3d predictedPos) {
      double distance = predictedPos.distanceTo(explosion) / (double)BED_EXPLOSION_SIZE;
      AxisAlignedBB box = entity.getEntityBoundingBox()
         .offset(predictedPos.x - entity.posX, predictedPos.y - entity.posY, predictedPos.z - entity.posZ);
      double density = 0.0;

      try {
         density = (double)mc.world.getBlockDensity(explosion, box);
      } catch (Exception var10) {
      }

      double v = (1.0 - distance) * density;
      float raw = (float)((int)((v * v + v) / 2.0 * 7.0 * (double)BED_EXPLOSION_SIZE + 1.0));
      return entity instanceof EntityLivingBase
         ? DamageUtil.getBlastReduction(
            (EntityLivingBase)entity,
            DamageUtil.getDamageMultiplied(raw),
            new Explosion(mc.world, null, explosion.x, explosion.y, explosion.z, BED_EXPLOSION_POWER, false, true)
         )
         : raw;
   }

   private EnumFacing miningSide(BlockPos pos) {
      for(EnumFacing facing : EnumFacing.values()) {
         if (mc.world.getBlockState(pos.offset(facing)).getBlock() == Blocks.AIR) {
            return facing;
         }
      }

      return EnumFacing.UP;
   }

   private EnumFacing firstPlaceableSide(BlockPos pos) {
      for(EnumFacing facing : EnumFacing.values()) {
         if (this.isSolid(pos.offset(facing))) {
            return facing;
         }
      }

      return this.strictDirection.getValue() ? null : EnumFacing.UP;
   }

   private boolean isReplaceable(BlockPos pos) {
      IBlockState state = mc.world.getBlockState(pos);
      return state.getBlock() == Blocks.AIR || state.getBlock().isReplaceable(mc.world, pos);
   }

   private boolean isSolid(BlockPos pos) {
      IBlockState state = mc.world.getBlockState(pos);
      return state.isFullBlock() || state.getMaterial().isSolid() && state.getCollisionBoundingBox(mc.world, pos) != null;
   }

   /** True when the position holds the given half of a bed. */
   public static boolean checkBedBlock(BlockPos pos, BlockBed.EnumPartType part) {
      IBlockState state = mc.world.getBlockState(pos);
      return state.getBlock() instanceof BlockBed && state.getValue(BlockBed.PART) == part;
   }

   private boolean isBed(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.getItem() == Items.BED;
   }

   private boolean hasBedSomewhere() {
      if (this.isBed(mc.player.getHeldItemOffhand())) {
         return true;
      } else {
         for(int slot = 0; slot < 36; ++slot) {
            if (this.isBed(mc.player.inventory.getStackInSlot(slot))) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean hasObsidian() {
      return InventoryUtil.findHotbarBlock(Blocks.OBSIDIAN) != -1;
   }

   private void pushPredictorSettings() {
      this.predictor
         .configure(
            this.maxLookahead.getValue(),
            this.lookaheadIncrement.getValue(),
            this.latencySync.getValue(),
            this.verticalExtrapolation.getValue(),
            this.axisSplit.getValue(),
            this.holeExitClamp.getValue(),
            this.elevatedHoleClamp.getValue(),
            this.stepCorrection.getValue(),
            this.maxSteps.getValue(),
            this.stepSpeedThreshold.getValue(),
            this.directionResetAngle.getValue(),
            this.airborneThreshold.getValue(),
            this.gravityRamp.getValue(),
            this.fallAcceleration.getValue(),
            this.fallCurve.getValue()
         );
   }

   // ---- supporting types ----

   /** A candidate placement's geometry, plus the upper bound the worker ranked it by. */
   public static final class CalcInfo {
      public final EnumFacing side;
      public final BlockPos pos;
      public final Vec3d hitVec;
      public final BlockPos basePlaceFoot;
      public final BlockPos basePlaceHead;
      /** Optimistic damage estimate, used only for ranking. Never reported to the user. */
      public final float bound;

      public CalcInfo(EnumFacing side, BlockPos pos, Vec3d hitVec, BlockPos basePlaceFoot, BlockPos basePlaceHead, float bound) {
         this.side = side;
         this.pos = pos;
         this.hitVec = hitVec;
         this.basePlaceFoot = basePlaceFoot;
         this.basePlaceHead = basePlaceHead;
         this.bound = bound;
      }
   }

   /**
    * The off-thread half of the search: enumerate, validate, rank.
    *
    * Reads nothing but its own immutable fields and the {@link TerrainSnapshot}, which is what makes
    * it safe to run on a worker. It never computes a real damage figure - only an upper bound used
    * to decide which candidates are worth pricing properly on the main thread.
    */
   private static final class SearchRequest implements java.util.concurrent.Callable<List<BedAura.CalcInfo>> {
      /** How many candidates to hand back for exact pricing. */
      private static final int SHORTLIST = 16;

      private final TerrainSnapshot snapshot;
      private final BlockPos centre;
      private final Vec3d eyes;
      private final Vec3d targetPos;
      private final float range;
      private final float basePlaceRange;
      private final double playerY;
      private final float basePlaceMaxY;
      private final float minDamage;
      private final float damageMultiplier;
      private final boolean allowBase;

      SearchRequest(
         TerrainSnapshot snapshot,
         BlockPos centre,
         Vec3d eyes,
         Vec3d targetPos,
         float range,
         float basePlaceRange,
         double playerY,
         float basePlaceMaxY,
         float minDamage,
         float damageMultiplier,
         boolean allowBase
      ) {
         this.snapshot = snapshot;
         this.centre = centre;
         this.eyes = eyes;
         this.targetPos = targetPos;
         this.range = range;
         this.basePlaceRange = basePlaceRange;
         this.playerY = playerY;
         this.basePlaceMaxY = basePlaceMaxY;
         this.minDamage = minDamage;
         this.damageMultiplier = damageMultiplier;
         this.allowBase = allowBase;
      }

      @Override
      public List<BedAura.CalcInfo> call() {
         List<BedAura.CalcInfo> candidates = new ArrayList<>();
         int radius = MathHelper.ceil(this.range);
         float rangeSq = this.range * this.range;

         for(int dx = -radius; dx <= radius; ++dx) {
            for(int dy = -radius; dy <= radius; ++dy) {
               for(int dz = -radius; dz <= radius; ++dz) {
                  int x = this.centre.getX() + dx;
                  int y = this.centre.getY() + dy;
                  int z = this.centre.getZ() + dz;
                  if (!(this.eyes.squareDistanceTo((double)x + 0.5, (double)y + 0.5, (double)z + 0.5) > (double)rangeSq)
                     && this.snapshot.isReplaceable(x, y, z)) {
                     float bound = this.upperBound(x, y, z);
                     // Nothing here can ever beat the damage floor, so skip the facings entirely.
                     if (!(bound < this.minDamage)) {
                        BlockPos foot = new BlockPos(x, y, z);

                        for(EnumFacing side : EnumFacing.HORIZONTALS) {
                           BedAura.CalcInfo candidate = this.build(foot, side, bound);
                           if (candidate != null) {
                              candidates.add(candidate);
                           }
                        }
                     }
                  }
               }
            }
         }

         candidates.sort(Comparator.comparingDouble(candidate -> -candidate.bound));
         return candidates.size() <= SHORTLIST ? candidates : new ArrayList<>(candidates.subList(0, SHORTLIST));
      }

      /** Validates one foot position and facing against the snapshot. */
      private BedAura.CalcInfo build(BlockPos foot, EnumFacing side, float bound) {
         BlockPos head = foot.offset(side);
         if (!this.snapshot.isReplaceable(head)) {
            return null;
         } else {
            boolean footSupported = this.snapshot.isSolid(foot.down());
            boolean headSupported = this.snapshot.isSolid(head.down());
            BlockPos basePlaceFoot = null;
            BlockPos basePlaceHead = null;
            if (!footSupported || !headSupported) {
               if (!this.allowBase || Math.abs((double)foot.getY() - this.playerY) > (double)this.basePlaceMaxY) {
                  return null;
               }

               if (!footSupported) {
                  if (!this.snapshot.isReplaceable(foot.down()) || !this.inBaseRange(foot.down())) {
                     return null;
                  }

                  basePlaceFoot = foot.down();
               }

               if (!headSupported) {
                  if (!this.snapshot.isReplaceable(head.down()) || !this.inBaseRange(head.down())) {
                     return null;
                  }

                  basePlaceHead = head.down();
               }
            }

            Vec3d hitVec = new Vec3d((double)foot.getX() + 0.5, (double)foot.getY(), (double)foot.getZ() + 0.5);
            return new BedAura.CalcInfo(side, foot, hitVec, basePlaceFoot, basePlaceHead, bound);
         }
      }

      private boolean inBaseRange(BlockPos pos) {
         return this.eyes.squareDistanceTo((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5)
            <= (double)(this.basePlaceRange * this.basePlaceRange);
      }

      /**
       * Highest damage this position could possibly deal.
       *
       * Assumes perfect line of sight - a block density of 1.0 - and skips armour entirely. Both
       * simplifications can only ever overstate the figure, which is exactly what a ranking bound
       * has to do: the real damage computed later is always lower, so nothing that deserved to be
       * on the shortlist gets dropped from it.
       */
      private float upperBound(int x, int y, int z) {
         double distance = this.targetPos.distanceTo(new Vec3d((double)x + 0.5, (double)y + 0.5, (double)z + 0.5)) / (double)BED_EXPLOSION_SIZE;
         if (distance >= 1.0) {
            return 0.0F;
         } else {
            double v = 1.0 - distance;
            float raw = (float)((int)((v * v + v) / 2.0 * 7.0 * (double)BED_EXPLOSION_SIZE + 1.0));
            return raw * this.damageMultiplier;
         }
      }
   }

   /** A candidate placement priced for both parties. */
   public static final class DamageInfo {
      public final BedAura.CalcInfo calcInfo;
      public final float targetDamage;
      public final float selfDamage;
      public final BlockPos basePlaceFoot;
      public final BlockPos basePlaceHead;

      public DamageInfo(BedAura.CalcInfo calcInfo, float targetDamage, float selfDamage, BlockPos basePlaceFoot, BlockPos basePlaceHead) {
         this.calcInfo = calcInfo;
         this.targetDamage = targetDamage;
         this.selfDamage = selfDamage;
         this.basePlaceFoot = basePlaceFoot;
         this.basePlaceHead = basePlaceHead;
      }

      public boolean needsBase() {
         return this.basePlaceFoot != null || this.basePlaceHead != null;
      }
   }

   /** The committed placement for this cycle. */
   public static final class PlaceInfo {
      public final BlockPos basePos;
      public final BlockPos bedPosFoot;
      public final BlockPos bedPosHead;
      public final EnumFacing direction;
      public final Vec3d hitVec;
      public final float targetDamage;
      public final boolean needsBasePlace;
      public final BlockPos basePlaceFoot;
      public final BlockPos basePlaceHead;

      public PlaceInfo(
         BlockPos basePos,
         BlockPos bedPosFoot,
         BlockPos bedPosHead,
         EnumFacing direction,
         Vec3d hitVec,
         float targetDamage,
         boolean needsBasePlace,
         BlockPos basePlaceFoot,
         BlockPos basePlaceHead
      ) {
         this.basePos = basePos;
         this.bedPosFoot = bedPosFoot;
         this.bedPosHead = bedPosHead;
         this.direction = direction;
         this.hitVec = hitVec;
         this.targetDamage = targetDamage;
         this.needsBasePlace = needsBasePlace;
         this.basePlaceFoot = basePlaceFoot;
         this.basePlaceHead = basePlaceHead;
      }
   }

   /**
    * Draws the pending placement.
    *
    * Kept as an inner class so it can read the module's settings directly while keeping all the
    * animation bookkeeping - timestamps, previous positions, tweens - out of the placement logic.
    */
   private final class Renderer {
      /** Bed footprint, in the local space of a bed facing south. */
      private static final double BED_HEIGHT = 0.5625;

      private BlockPos currentFoot;
      private BlockPos previousFoot;
      private EnumFacing currentFacing;
      private EnumFacing previousFacing;
      private long spawnTime;
      private long despawnTime;
      private long moveTime;
      private String damageText = "";

      void reset() {
         this.currentFoot = null;
         this.previousFoot = null;
         this.currentFacing = null;
         this.previousFacing = null;
         this.spawnTime = 0L;
         this.despawnTime = 0L;
         this.moveTime = 0L;
         this.damageText = "";
      }

      void onPlaced(BedAura.PlaceInfo info) {
         if (this.currentFoot == null) {
            this.spawnTime = System.currentTimeMillis();
         }

         if (!info.bedPosFoot.equals(this.currentFoot) || info.direction != this.currentFacing) {
            this.previousFoot = this.currentFoot;
            this.previousFacing = this.currentFacing;
            this.moveTime = System.currentTimeMillis();
         }

         this.currentFoot = info.bedPosFoot;
         this.currentFacing = info.direction;
         this.despawnTime = 0L;
         this.damageText = String.format("%.1f", info.targetDamage);
      }

      void render3D() {
         if (BedAura.this.placeInfo == null) {
            if (this.currentFoot != null && this.despawnTime == 0L) {
               this.despawnTime = System.currentTimeMillis();
            }
         } else {
            this.onPlaced(BedAura.this.placeInfo);
         }

         if (this.currentFoot != null && this.currentFacing != null) {
            long now = System.currentTimeMillis();
            float spawnProgress = this.progress(now - this.spawnTime, BedAura.this.spawnLength());
            float despawnProgress = this.despawnTime == 0L ? 0.0F : this.progress(now - this.despawnTime, BedAura.this.despawnLength());
            if (this.despawnTime != 0L && despawnProgress >= 1.0F) {
               this.reset();
            } else {
               float alpha = 1.0F;
               double scale = 1.0;
               double rise = 0.0;
               switch(BedAura.this.spawnAnimation.getValue()) {
                  case FADE:
                     alpha = spawnProgress;
                     break;
                  case GROW:
                     scale = (double)spawnProgress;
                     break;
                  case RISE:
                     rise = (double)(1.0F - spawnProgress) * -1.0;
                  case STATIC:
               }

               if (this.despawnTime != 0L) {
                  alpha *= 1.0F - despawnProgress;
                  scale *= (double)(1.0F - despawnProgress * 0.5F);
                  rise -= (double)despawnProgress;
               }

               // MORPH slides between successive placements rather than teleporting. The slide
               // window widens while the target is moving, because placements then change every
               // cycle and a short tween would read as a jitter rather than a movement.
               double slide = 1.0;
               if (BedAura.this.renderStyle.getValue() == RenderStyle.MORPH && this.previousFoot != null) {
                  boolean moving = BedAura.this.target != null && BedAura.this.predictor.getSpeed(BedAura.this.target) > 0.05;
                  long window = (long)(moving ? BedAura.this.movingLength.getValue() : BedAura.this.morphSpeed.getValue()).intValue();
                  slide = (double)this.progress(now - this.moveTime, window);
               }

               double originX = this.interpolate(this.previousFoot == null ? this.currentFoot.getX() : this.previousFoot.getX(), this.currentFoot.getX(), slide);
               double originY = this.interpolate(this.previousFoot == null ? this.currentFoot.getY() : this.previousFoot.getY(), this.currentFoot.getY(), slide)
                  + rise;
               double originZ = this.interpolate(this.previousFoot == null ? this.currentFoot.getZ() : this.previousFoot.getZ(), this.currentFoot.getZ(), slide);
               float facingAngle = this.tweenFacing(now);
               AxisAlignedBB foot = this.rotated(originX, originY, originZ, facingAngle, 0.0, scale);
               AxisAlignedBB head = this.rotated(originX, originY, originZ, facingAngle, 1.0, scale);
               Color footPaint = BedAura.this.colorMode.getValue() == ColorMode.SINGLE
                  ? BedAura.this.color.getValue()
                  : BedAura.this.footColor.getValue();
               Color headPaint = BedAura.this.colorMode.getValue() == ColorMode.SINGLE
                  ? BedAura.this.color.getValue()
                  : BedAura.this.headColor.getValue();
               int fill = (int)((float)BedAura.this.fillAlpha.getValue().intValue() * alpha);
               int outline = (int)((float)BedAura.this.outlineAlpha.getValue().intValue() * alpha);
               RenderUtil.drawBBFill(foot, footPaint, fill);
               RenderUtil.drawBBFill(head, headPaint, fill);
               RenderUtil.drawBlockOutline(foot, withAlpha(footPaint, outline), BedAura.this.lineWidth.getValue());
               RenderUtil.drawBlockOutline(head, withAlpha(headPaint, outline), BedAura.this.lineWidth.getValue());
            }
         }
      }

      void render2D() {
         if (BedAura.this.renderDamage.getValue() && this.currentFoot != null && !this.damageText.isEmpty()) {
            Vec3d screen = RenderUtil.get2DPos(
               (double)this.currentFoot.getX() + 0.5 - mc.getRenderManager().viewerPosX,
               (double)this.currentFoot.getY() + 1.0 - mc.getRenderManager().viewerPosY,
               (double)this.currentFoot.getZ() + 0.5 - mc.getRenderManager().viewerPosZ
            );
            if (screen != null && screen.z >= 0.0 && screen.z < 1.0) {
               Managers.TEXT
                  .drawString(
                     this.damageText,
                     (float)screen.x - Managers.TEXT.getStringWidth(this.damageText) / 2.0F,
                     (float)screen.y,
                     BedAura.this.footColor.getValue().getRGB(),
                     true
                  );
            }
         }
      }

      /** Builds one half of the bed, rotated into the placement facing. */
      private AxisAlignedBB rotated(double originX, double originY, double originZ, float angle, double forward, double scale) {
         double radians = Math.toRadians((double)angle);
         double sin = Math.sin(radians);
         double cos = Math.cos(radians);
         // Local footprint: half a block either side, one block deep, offset forward for the head.
         double nearZ = forward;
         double farZ = forward + 1.0;
         double minX = Double.MAX_VALUE;
         double minZ = Double.MAX_VALUE;
         double maxX = -Double.MAX_VALUE;
         double maxZ = -Double.MAX_VALUE;

         for(int corner = 0; corner < 4; ++corner) {
            double localX = (corner & 1) == 0 ? -0.5 : 0.5;
            double localZ = (corner & 2) == 0 ? nearZ : farZ;
            double worldX = localX * cos - localZ * sin;
            double worldZ = localX * sin + localZ * cos;
            minX = Math.min(minX, worldX);
            maxX = Math.max(maxX, worldX);
            minZ = Math.min(minZ, worldZ);
            maxZ = Math.max(maxZ, worldZ);
         }

         double centreX = originX + 0.5;
         double centreZ = originZ + 0.5;
         double shrink = (1.0 - scale) * 0.5;
         return new AxisAlignedBB(
            centreX + minX + shrink,
            originY,
            centreZ + minZ + shrink,
            centreX + maxX - shrink,
            originY + BED_HEIGHT * scale,
            centreZ + maxZ - shrink
         );
      }

      /** Eases the drawn facing toward the current one so the box turns instead of snapping. */
      private float tweenFacing(long now) {
         float current = this.currentFacing.getHorizontalAngle();
         if (this.previousFacing != null && this.previousFacing != this.currentFacing) {
            float previous = this.previousFacing.getHorizontalAngle();
            float progress = this.progress(now - this.moveTime, (long)BedAura.this.rotateLength.getValue().intValue());
            return previous + MathHelper.wrapDegrees(current - previous) * progress;
         } else {
            return current;
         }
      }

      private double interpolate(double from, double to, double progress) {
         return from + (to - from) * progress;
      }

      /** Eased 0..1 over a duration, quadratic out. */
      private float progress(long elapsed, long duration) {
         if (duration <= 0L) {
            return 1.0F;
         } else {
            float linear = MathHelper.clamp((float)elapsed / (float)duration, 0.0F, 1.0F);
            return 1.0F - (1.0F - linear) * (1.0F - linear);
         }
      }
   }

   private long spawnLength() {
      switch(this.spawnAnimation.getValue()) {
         case FADE:
            return (long)this.fadeLength.getValue().intValue();
         case GROW:
            return (long)this.growLength.getValue().intValue();
         case RISE:
            return (long)this.riseLength.getValue().intValue();
         default:
            return 1L;
      }
   }

   private long despawnLength() {
      return (long)Math.max(this.shrinkLength.getValue(), this.fallLength.getValue());
   }

   private static Color withAlpha(Color color, int alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), MathHelper.clamp(alpha, 0, 255));
   }
}
