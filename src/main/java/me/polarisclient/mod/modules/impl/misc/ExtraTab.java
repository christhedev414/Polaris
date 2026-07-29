package me.polarisclient.mod.modules.impl.misc;

import me.polarisclient.mod.modules.Category;
import me.polarisclient.mod.modules.Module;
import me.polarisclient.mod.modules.settings.Setting;

public class ExtraTab extends Module {
   public static ExtraTab INSTANCE = new ExtraTab();
   public final Setting<Integer> size = this.add(new Setting<>("Size", 250, 1, 1000));

   public ExtraTab() {
      super("ExtraTab", "Extends Tab", Category.MISC);
      INSTANCE = this;
   }
}
