//Deobfuscated with https://github.com/SimplyProgrammer/Minecraft-Deobfuscator3000 using mappings "G:\PortableSoft\JBY\MC_Deobf3000\1.12-MCP-Mappings"!

package me.polarisclient.mod.modules.impl.client;

import me.polarisclient.api.util.FadeUtils;
import me.polarisclient.mod.modules.Category;
import me.polarisclient.mod.modules.Module;
import me.polarisclient.mod.modules.settings.Setting;

public class GuiAnimation extends Module {
   public static GuiAnimation INSTANCE;
   private final Setting<Integer> inventoryTime = this.add(new Setting<>("InventoryTime", 500, 0, 2000));
   public static FadeUtils inventoryFade = new FadeUtils(500L);

   public GuiAnimation() {
      super("GuiAnimation", "", Category.CLIENT);
      INSTANCE = this;
   }

   @Override
   public void onTick() {
      inventoryFade.setLength((long)this.inventoryTime.getValue().intValue());
      if (mc.currentScreen == null) {
         inventoryFade.reset();
      }
   }
}
