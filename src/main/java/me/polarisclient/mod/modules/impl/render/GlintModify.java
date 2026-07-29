package me.polarisclient.mod.modules.impl.render;

import java.awt.Color;
import me.polarisclient.mod.modules.Category;
import me.polarisclient.mod.modules.Module;
import me.polarisclient.mod.modules.settings.Setting;

public class GlintModify extends Module {
   public static GlintModify INSTANCE = new GlintModify();
   public final Setting<Color> color = this.add(new Setting<>("Color", new Color(-557395713, true)).hideAlpha());

   public GlintModify() {
      super("GlintModify", "Changes the enchant glint color", Category.RENDER);
      INSTANCE = this;
   }

   public static Color getColor() {
      return INSTANCE.color.getValue();
   }
}
