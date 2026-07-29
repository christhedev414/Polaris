//Deobfuscated with https://github.com/SimplyProgrammer/Minecraft-Deobfuscator3000 using mappings "G:\PortableSoft\JBY\MC_Deobf3000\1.12-MCP-Mappings"!

package me.polarisclient.mod.modules.impl.client;

import me.polarisclient.mod.modules.Category;
import me.polarisclient.mod.modules.Module;

public class Appearance extends Module {
   public Appearance() {
      super("HUDEditor", "Drag HUD elements all over your screen", Category.CLIENT);
   }

   @Override
   public void onEnable() {
      mc.displayGuiScreen(me.polarisclient.mod.gui.screen.Appearance.getClickGui());
   }

   @Override
   public void onTick() {
      if (!(mc.currentScreen instanceof me.polarisclient.mod.gui.screen.Appearance)) {
         this.disable();
      }
   }
}
