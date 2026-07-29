package me.polarisclient.mod.modules.impl.misc;

import me.polarisclient.mod.commands.Command;
import me.polarisclient.mod.modules.Category;
import me.polarisclient.mod.modules.Module;
import me.polarisclient.mod.modules.settings.Setting;

public class NullPatcher extends Module {
   public static NullPatcher INSTANCE = new NullPatcher();
   public final Setting<Boolean> debug = this.add(new Setting<>("Debug", true));

   public NullPatcher() {
      super("NullPatcher", "anti null kick", Category.MISC);
      INSTANCE = this;
   }

   public void sendWarning(Throwable Throwable) {
      if (this.debug.getValue()) {
         Command.sendMessage("§4[!] An error occurred! reason: §7" + Throwable.getMessage());
      }

      Throwable.printStackTrace();
   }
}
