package me.polarisclient.mod.modules.impl.movement;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import me.polarisclient.api.util.PositionUtil;
import me.polarisclient.api.util.Timer;
import me.polarisclient.api.util.path.Goal;
import me.polarisclient.api.util.path.PathFinder;
import me.polarisclient.mod.commands.Command;
import me.polarisclient.mod.modules.Category;
import me.polarisclient.mod.modules.Module;
import me.polarisclient.mod.modules.settings.Setting;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

public class Pathfind extends Module {
   public static Pathfind INSTANCE;
   private final Setting<Integer> maxFall = this.add(new Setting<>("MaxFall", 3, 1, 20));
   private final Setting<Integer> maxNodes = this.add(new Setting<>("MaxNodes", 8000, 1000, 50000));
   private final Setting<Integer> repathDelay = this.add(new Setting<>("RepathDelay", 500, 100, 5000));
   private final Setting<Boolean> sprint = this.add(new Setting<>("Sprint", true));
   private final Setting<Boolean> render = this.add(new Setting<>("Render", true));
   private final Setting<Float> lineWidth = this.add(new Setting<>("Width", 1.5F, 0.1F, 5.0F, v -> this.render.getValue()));
   private final Setting<Color> color = this.add(new Setting<>("Color", new Color(90, 190, 255), v -> this.render.getValue()));
   private final List<BlockPos> path = new ArrayList<>();
   private final Timer repathTimer = new Timer();
   private Goal goal;
   private int index;

   public Pathfind() {
      super("Pathfind", "Walks to a goal using A* pathfinding", Category.MOVEMENT);
      INSTANCE = this;
   }

   public void setGoal(Goal goal) {
      this.goal = goal;
      this.path.clear();
      this.index = 0;
      this.repathTimer.reset();
      if (!this.isOn()) {
         this.enable();
      }
   }

   public Goal getGoal() {
      return this.goal;
   }

   public void stop() {
      if (this.isOn()) {
         this.disable();
      } else {
         this.onDisable();
      }
   }

   @Override
   public void onEnable() {
      this.path.clear();
      this.index = 0;
      this.repathTimer.reset();
      if (this.goal == null && !nullCheck()) {
         this.sendMessage("No goal set. Use " + Command.getCommandPrefix() + "goto <x> <y> <z>.");
      }
   }

   @Override
   public void onDisable() {
      this.goal = null;
      this.path.clear();
      this.index = 0;
   }

   @Override
   public void onTick() {
      if (!nullCheck() && this.goal != null) {
         BlockPos feet = PositionUtil.getPosition();
         if (this.goal.isFinished(feet)) {
            this.sendMessage("Arrived at " + this.goal + ".");
            this.stop();
         } else {
            this.advance(feet);
            if (this.needsRepath(feet)) {
               this.repath(feet);
            }
         }
      }
   }

   /** Consumes any nodes the player is already standing in. */
   private void advance(BlockPos feet) {
      while(this.index < this.path.size()) {
         BlockPos node = this.path.get(this.index);
         if (node.getX() != feet.getX() || node.getZ() != feet.getZ() || Math.abs(node.getY() - feet.getY()) > 1) {
            break;
         }

         ++this.index;
      }
   }

   private boolean needsRepath(BlockPos feet) {
      if (this.path.isEmpty() || this.index >= this.path.size()) {
         return true;
      } else if (feet.distanceSq(this.path.get(this.index)) > 16.0) {
         return true;
      } else {
         // A path that stops short of the goal is a partial one, so keep extending it as we walk.
         return !this.goal.isFinished(this.path.get(this.path.size() - 1))
            && this.repathTimer.passedMs((long)this.repathDelay.getValue().intValue());
      }
   }

   private void repath(BlockPos feet) {
      this.repathTimer.reset();
      List<BlockPos> found = PathFinder.find(feet, this.goal, this.maxNodes.getValue(), this.maxFall.getValue());
      this.path.clear();
      this.index = 0;
      if (found.isEmpty()) {
         this.sendMessage("No path to " + this.goal + ".");
         this.stop();
      } else {
         this.path.addAll(found);
         this.index = Math.min(1, this.path.size() - 1);
      }
   }

   @SubscribeEvent
   public void onInputUpdate(InputUpdateEvent event) {
      if (!nullCheck() && this.goal != null && this.index < this.path.size()) {
         BlockPos target = this.path.get(this.index);
         double dx = (double)target.getX() + 0.5 - mc.player.posX;
         double dz = (double)target.getZ() + 0.5 - mc.player.posZ;
         // Steer relative to where the player is already looking, so the camera stays free.
         double delta = Math.toRadians(Math.toDegrees(Math.atan2(dz, dx)) - 90.0 - (double)mc.player.rotationYaw);
         MovementInput input = event.getMovementInput();
         input.moveForward = (float)Math.cos(delta);
         input.moveStrafe = (float)(-Math.sin(delta));
         if (target.getY() > MathHelper.floor(mc.player.posY) && mc.player.onGround) {
            input.jump = true;
         }

         if (this.sprint.getValue() && input.moveForward > 0.5F && !PositionUtil.inLiquid()) {
            mc.player.setSprinting(true);
         }
      }
   }

   @SubscribeEvent
   public void onRenderWorld(RenderWorldLastEvent event) {
      if (this.render.getValue() && !this.path.isEmpty()) {
         List<BlockPos> snapshot = new ArrayList<>(this.path);
         Color lineColor = this.color.getValue();
         GlStateManager.pushMatrix();
         GlStateManager.disableDepth();
         GlStateManager.disableLighting();
         GlStateManager.depthMask(false);
         GlStateManager.disableAlpha();
         GlStateManager.disableCull();
         GlStateManager.enableBlend();
         GL11.glDisable(3553);
         GL11.glEnable(2848);
         GL11.glBlendFunc(770, 771);
         GL11.glLineWidth(this.lineWidth.getValue());
         GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
         Tessellator tessellator = Tessellator.getInstance();
         BufferBuilder builder = tessellator.getBuffer();
         builder.begin(3, DefaultVertexFormats.POSITION_COLOR);

         for(BlockPos pos : snapshot) {
            builder.pos(
                  (double)pos.getX() + 0.5 - mc.getRenderManager().viewerPosX,
                  (double)pos.getY() + 0.5 - mc.getRenderManager().viewerPosY,
                  (double)pos.getZ() + 0.5 - mc.getRenderManager().viewerPosZ
               )
               .color(
                  (float)lineColor.getRed() / 255.0F,
                  (float)lineColor.getGreen() / 255.0F,
                  (float)lineColor.getBlue() / 255.0F,
                  (float)lineColor.getAlpha() / 255.0F
               )
               .endVertex();
         }

         tessellator.draw();
         GlStateManager.depthMask(true);
         GlStateManager.enableLighting();
         GlStateManager.enableDepth();
         GlStateManager.enableAlpha();
         GlStateManager.popMatrix();
         GL11.glEnable(3553);
         GL11.glPolygonMode(1032, 6914);
      }
   }
}
