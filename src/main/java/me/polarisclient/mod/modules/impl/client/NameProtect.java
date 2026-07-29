package me.polarisclient.mod.modules.impl.client;

import me.polarisclient.mod.modules.Category;
import me.polarisclient.mod.modules.Module;
import me.polarisclient.mod.modules.settings.Setting;

public class NameProtect extends Module {
   public static NameProtect INSTANCE;
   public final Setting<String> name = this.add(new Setting<>("Name", "Me"));

   public NameProtect() {
      super("NameProtect", "To keep your alts in secret", Category.CLIENT);
      INSTANCE = this;
   }
}
