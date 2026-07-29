//Deobfuscated with https://github.com/SimplyProgrammer/Minecraft-Deobfuscator3000 using mappings "G:\PortableSoft\JBY\MC_Deobf3000\1.12-MCP-Mappings"!

package me.polarisclient.mod.commands.impl;

import me.polarisclient.mod.commands.Command;
import me.polarisclient.mod.modules.impl.misc.ToolTips;

public class PeekCommand extends Command {
   public PeekCommand() {
      super("peek", new String[]{""});
   }

   @Override
   public void execute(String[] commands) {
      ToolTips.drawShulkerGui(mc.player.getHeldItemMainhand(), mc.player.getHeldItemMainhand().getDisplayName());
   }
}
