package me.polarisclient.api.events.impl;

import me.polarisclient.api.events.Event;

public class UpdateWalkingPlayerEvent extends Event {
   public UpdateWalkingPlayerEvent(int stage) {
      super(stage);
   }
}
