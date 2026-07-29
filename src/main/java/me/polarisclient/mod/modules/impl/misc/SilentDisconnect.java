package me.polarisclient.mod.modules.impl.misc;

import me.polarisclient.mod.modules.Category;
import me.polarisclient.mod.modules.Module;

public class SilentDisconnect extends Module {
   public static SilentDisconnect INSTANCE = new SilentDisconnect();

   public SilentDisconnect() {
      super("SilentDisconnect", "Silent disconnect", Category.MISC);
      INSTANCE = this;
   }
}
