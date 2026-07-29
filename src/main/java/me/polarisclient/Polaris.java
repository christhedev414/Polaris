//Deobfuscated with https://github.com/SimplyProgrammer/Minecraft-Deobfuscator3000 using mappings "G:\PortableSoft\JBY\MC_Deobf3000\1.12-MCP-Mappings"!

package me.polarisclient;

import java.io.InputStream;
import java.nio.ByteBuffer;
import me.polarisclient.api.managers.Managers;
import me.polarisclient.api.util.render.RenderUtil;
import me.polarisclient.mod.gui.screen.Gui;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.util.Util.EnumOS;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

@Mod(
   modid = "polaris",
   name = "Polaris",
   version = "alpha"
)
public class Polaris {
   public static final String MODID = "polaris";
   public static final String MODNAME = "Polaris";
   public static final String MODVERISON = "alpha";
   public static final Logger LOGGER = LogManager.getLogger("Polaris");
   @Instance
   public static Polaris INSTANCE;

   public static void load() {
      LOGGER.info("Loading Polaris alpha...");
      Managers.load();
      if (Gui.INSTANCE == null) {
         Gui.INSTANCE = new Gui();
      }

      LOGGER.info("Polaris alpha successfully loaded!\n");
   }

   public static void unload(boolean force) {
      LOGGER.info("Unloading Polaris alpha...");
      Managers.unload(force);
      LOGGER.info("Polaris alpha successfully unloaded!\n");
   }

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
      Display.setTitle("Polaris alpha: Loading...");
      if (Util.getOSType() != EnumOS.OSX) {
         try (
            InputStream inputStream16x = Minecraft.class.getResourceAsStream("/assets/minecraft/textures/polaris/constant/icon16x.png");
            InputStream inputStream32x = Minecraft.class.getResourceAsStream("/assets/minecraft/textures/polaris/constant/icon32x.png");
         ) {
            ByteBuffer[] icons = new ByteBuffer[]{RenderUtil.readImageToBuffer(inputStream16x), RenderUtil.readImageToBuffer(inputStream32x)};
            Display.setIcon(icons);
         } catch (Exception var34) {
            LOGGER.error("Polaris alpha couldn't set the window icon!", var34);
         }
      }
   }

   @EventHandler
   public void init(FMLInitializationEvent event) {
      load();
   }
}
