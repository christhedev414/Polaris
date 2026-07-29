package me.polarisclient.mod.commands.impl;

import me.polarisclient.Polaris;
import me.polarisclient.mod.commands.Command;

public class UnloadCommand extends Command {
   public UnloadCommand() {
      super("unload", new String[0]);
   }

   @Override
   public void execute(String[] commands) {
      Polaris.unload(true);
   }
}
