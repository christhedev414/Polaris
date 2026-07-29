package me.polarisclient.api.events.impl;

import me.polarisclient.api.events.Event;
import me.polarisclient.mod.Mod;
import me.polarisclient.mod.modules.settings.Setting;
import net.minecraftforge.fml.common.eventhandler.Cancelable;

@Cancelable
public class ClientEvent extends Event {
   private Mod mod;
   private Setting setting;

   public ClientEvent(int stage, Mod mod) {
      super(stage);
      this.mod = mod;
   }

   public ClientEvent(Setting setting) {
      super(2);
      this.setting = setting;
   }

   public Mod getMod() {
      return this.mod;
   }

   public Setting getSetting() {
      return this.setting;
   }
}
