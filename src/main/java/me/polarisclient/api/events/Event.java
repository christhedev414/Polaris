package me.polarisclient.api.events;

import me.polarisclient.Polaris;

public class Event extends net.minecraftforge.fml.common.eventhandler.Event {
   private int stage;

   public Event() {
   }

   public Event(int stage) {
      this.stage = stage;
   }

   public int getStage() {
      return this.stage;
   }

   public void setStage(int stage) {
      this.stage = stage;
   }

   public void cancel() {
      try {
         this.setCanceled(true);
      } catch (Exception var2) {
         Polaris.LOGGER.info(this.getClass().toString() + " Isn't cancellable!");
      }
   }
}
