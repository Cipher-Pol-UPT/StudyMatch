package com.studymatch.utils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

// who has the app open, only in memory, a restart drops every connection anyway
@Component
public class PresenceTracker {

  // a user's open websocket sessions, one per tab
  private final Map<Long, Set<String>> sessions = new ConcurrentHashMap<>();

  // true for their first tab, compute keeps two tabs opening at once from both being first
  public boolean connect(Long number, String sessionId) {
    AtomicBoolean first = new AtomicBoolean();
    sessions.compute(number, (key, open) -> {
      Set<String> updated = open == null ? new HashSet<>() : open;
      first.set(updated.isEmpty());
      updated.add(sessionId);
      return updated;
    });
    return first.get();
  }

  // true when their last tab closes, a session closing twice only counts once
  public boolean disconnect(Long number, String sessionId) {
    AtomicBoolean last = new AtomicBoolean();
    sessions.computeIfPresent(number, (key, open) -> {
      last.set(open.remove(sessionId) && open.isEmpty());
      return open.isEmpty() ? null : open;
    });
    return last.get();
  }

  public boolean isOnline(Long number) {
    return sessions.containsKey(number);
  }

}
