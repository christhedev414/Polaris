package me.polarisclient.api.util.path;

import java.awt.Color;
import java.util.List;
import me.polarisclient.api.util.Wrapper;
import me.polarisclient.api.util.path.goal.Goal;
import me.polarisclient.api.util.path.movement.Movement;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

/**
 * Debug rendering for the pathfinder: the route, the nodes the search touched, and the goal.
 *
 * The explored-node cloud is the useful one when tuning. Its shape tells you immediately whether
 * the heuristic is doing its job - a tight cone toward the goal means good guidance, a fat sphere
 * around the player means the search is effectively running blind and the weights need attention.
 */
public final class PathRenderer implements Wrapper {
   /** GL primitive modes, spelled out because the raw integers are unreadable. */
   private static final int GL_LINES = 1;
   private static final int GL_POINTS = 0;

   private PathRenderer() {
   }

   /**
    * Draws the route, one line segment per movement, coloured by movement type. Segments already
    * walked are dimmed so it is obvious at a glance how far along the path the player is.
    */
   public static void renderPath(Path path, int currentIndex, float lineWidth) {
      if (path != null && !path.isEmpty()) {
         begin();
         GL11.glLineWidth(lineWidth);
         Tessellator tessellator = Tessellator.getInstance();
         BufferBuilder builder = tessellator.getBuffer();
         builder.begin(GL_LINES, DefaultVertexFormats.POSITION_COLOR);

         for(int i = 0; i < path.size(); ++i) {
            Movement movement = path.get(i);
            Color color = movement.getType().getDebugColor();
            int alpha = i < currentIndex ? 60 : 255;
            vertex(builder, movement.getFrom(), color, alpha);
            vertex(builder, movement.getTo(), color, alpha);
         }

         tessellator.draw();
         end();
      }
   }

   /** Draws every position the last search expanded, as a point cloud. */
   public static void renderExplored(List<BlockPos> explored, Color color, float pointSize) {
      if (explored != null && !explored.isEmpty()) {
         begin();
         GL11.glPointSize(pointSize);
         Tessellator tessellator = Tessellator.getInstance();
         BufferBuilder builder = tessellator.getBuffer();
         builder.begin(GL_POINTS, DefaultVertexFormats.POSITION_COLOR);

         for(BlockPos pos : explored) {
            vertex(builder, pos, color, color.getAlpha());
         }

         tessellator.draw();
         end();
      }
   }

   /** Draws a wireframe box around the goal position. */
   public static void renderGoal(Goal goal, Color color, float lineWidth) {
      if (goal != null) {
         BlockPos pos = goal.getRenderPos();
         double x = (double)pos.getX() - mc.getRenderManager().viewerPosX;
         double y = (double)pos.getY() - mc.getRenderManager().viewerPosY;
         double z = (double)pos.getZ() - mc.getRenderManager().viewerPosZ;
         begin();
         GL11.glLineWidth(lineWidth);
         Tessellator tessellator = Tessellator.getInstance();
         BufferBuilder builder = tessellator.getBuffer();
         builder.begin(GL_LINES, DefaultVertexFormats.POSITION_COLOR);
         float red = (float)color.getRed() / 255.0F;
         float green = (float)color.getGreen() / 255.0F;
         float blue = (float)color.getBlue() / 255.0F;
         float alpha = (float)color.getAlpha() / 255.0F;

         // Twelve edges of the unit cube, as six vertical-ish pairs plus the two square faces.
         for(int corner = 0; corner < 4; ++corner) {
            double cornerX = x + (double)(corner == 1 || corner == 2 ? 1 : 0);
            double cornerZ = z + (double)(corner >= 2 ? 1 : 0);
            builder.pos(cornerX, y, cornerZ).color(red, green, blue, alpha).endVertex();
            builder.pos(cornerX, y + 1.0, cornerZ).color(red, green, blue, alpha).endVertex();
         }

         for(int level = 0; level < 2; ++level) {
            double levelY = y + (double)level;

            for(int corner = 0; corner < 4; ++corner) {
               int next = (corner + 1) % 4;
               builder.pos(x + (double)(corner == 1 || corner == 2 ? 1 : 0), levelY, z + (double)(corner >= 2 ? 1 : 0))
                  .color(red, green, blue, alpha)
                  .endVertex();
               builder.pos(x + (double)(next == 1 || next == 2 ? 1 : 0), levelY, z + (double)(next >= 2 ? 1 : 0))
                  .color(red, green, blue, alpha)
                  .endVertex();
            }
         }

         tessellator.draw();
         end();
      }
   }

   /** Emits a vertex at the centre of a block, translated into camera-relative space. */
   private static void vertex(BufferBuilder builder, BlockPos pos, Color color, int alpha) {
      builder.pos(
            (double)pos.getX() + 0.5 - mc.getRenderManager().viewerPosX,
            (double)pos.getY() + 0.5 - mc.getRenderManager().viewerPosY,
            (double)pos.getZ() + 0.5 - mc.getRenderManager().viewerPosZ
         )
         .color((float)color.getRed() / 255.0F, (float)color.getGreen() / 255.0F, (float)color.getBlue() / 255.0F, (float)alpha / 255.0F)
         .endVertex();
   }

   private static void begin() {
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
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
   }

   private static void end() {
      GlStateManager.depthMask(true);
      GlStateManager.enableLighting();
      GlStateManager.enableDepth();
      GlStateManager.enableAlpha();
      GlStateManager.enableCull();
      GlStateManager.popMatrix();
      GL11.glEnable(3553);
      GL11.glDisable(2848);
   }
}
