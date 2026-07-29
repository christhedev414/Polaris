package me.polarisclient.mod.commands.impl;

import com.mojang.realmsclient.gui.ChatFormatting;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import me.polarisclient.mod.commands.Command;

public class ShrugCommand extends Command {
   public ShrugCommand() {
      super("shrug");
   }

   @Override
   public void execute(String[] commands) {
      String shrug = "¯\\_(\u30c4)_/¯";
      StringSelection stringSelection = new StringSelection(shrug);
      Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      clipboard.setContents(stringSelection, null);
      Command.sendMessage(ChatFormatting.GRAY + "copied le shrug to ur clipboard");
   }
}
