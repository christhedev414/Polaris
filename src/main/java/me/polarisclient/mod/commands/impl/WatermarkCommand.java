package me.polarisclient.mod.commands.impl;

import com.mojang.realmsclient.gui.ChatFormatting;
import me.polarisclient.mod.commands.Command;
import me.polarisclient.mod.modules.impl.client.FontMod;
import me.polarisclient.mod.modules.impl.client.HUD;

public class WatermarkCommand extends Command {
   public WatermarkCommand() {
      super("watermark", new String[]{"<watermark>"});
   }

   @Override
   public void execute(String[] commands) {
      if (commands.length == 2) {
         FontMod fontMod = FontMod.INSTANCE;
         boolean customFont = fontMod.isOn();
         if (commands[0] != null) {
            if (customFont) {
               fontMod.disable();
            }

            HUD.INSTANCE.watermarkString.setValue(commands[0]);
            if (customFont) {
               fontMod.enable();
            }

            sendMessage("Watermark set to " + ChatFormatting.GREEN + commands[0]);
         } else {
            sendMessage("Not a valid command... Possible usage: <New Watermark>");
         }
      }
   }
}
