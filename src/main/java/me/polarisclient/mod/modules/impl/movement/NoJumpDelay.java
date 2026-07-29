package me.polarisclient.mod.modules.impl.movement;

import me.polarisclient.mod.modules.Category;
import me.polarisclient.mod.modules.Module;

public final class NoJumpDelay extends Module {
   public static NoJumpDelay INSTANCE = new NoJumpDelay();

   public NoJumpDelay() {
      super("NoJumpDelay", "No jump delay", Category.MOVEMENT);
      INSTANCE = this;
   }
}
