package me.polarisclient.mod.commands.impl;

import com.mojang.realmsclient.gui.ChatFormatting;
import me.polarisclient.api.managers.Managers;
import me.polarisclient.mod.commands.Command;

public class PrefixCommand extends Command {
   public PrefixCommand() {
      super("prefix", new String[]{"<char>"});
   }

   @Override
   public void execute(String[] commands) {
      if (commands.length == 1) {
         Command.sendMessage(ChatFormatting.GREEN + "The current prefix is " + Managers.COMMANDS.getCommandPrefix());
      } else {
         Managers.COMMANDS.setPrefix(commands[0]);
         Command.sendMessage("Prefix changed to " + ChatFormatting.GRAY + commands[0]);
      }
   }
}
