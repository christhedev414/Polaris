package me.polarisclient.mod.modules.impl.movement;

import me.polarisclient.mod.modules.Category;
import me.polarisclient.mod.modules.Module;
import me.polarisclient.mod.modules.settings.Setting;

public class InventoryMove extends Module {
   public static InventoryMove INSTANCE = new InventoryMove();
   public final Setting<Boolean> sneak = this.add(new Setting<>("Sneak", false));

   public InventoryMove() {
      super("InvMove", "Allow walking on the interface", Category.MOVEMENT);
      INSTANCE = this;
   }
}
