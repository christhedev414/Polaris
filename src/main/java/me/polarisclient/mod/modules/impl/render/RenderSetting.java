package me.polarisclient.mod.modules.impl.render;

import me.polarisclient.mod.modules.Category;
import me.polarisclient.mod.modules.Module;
import me.polarisclient.mod.modules.settings.Setting;

public class RenderSetting extends Module {
   public static RenderSetting INSTANCE;
   public final Setting<Float> outlineWidth = this.add(new Setting<>("OutlineWidth", 1.0F, 0.1F, 4.0F));

   public RenderSetting() {
      super("RenderSetting", "idk", Category.RENDER);
      INSTANCE = this;
   }
}
