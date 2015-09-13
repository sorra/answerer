package sorra.answerer.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EventSeq {
  private String source;
  private List<Event> events = new ArrayList<>();

  public EventSeq(String source) {
    this.source = source;
  }

  public interface Event {
    int begin();
    int end();
  }

  public static class Insertion implements Event {
    String code;
    int nextIndex;

    public Insertion(String code, int nextIndex) {
      this.code = code;
      this.nextIndex = nextIndex;
    }
    @Override
    public int begin() {
      return nextIndex;
    }
    @Override
    public int end() {
      return nextIndex;
    }
  }

  public static class Deletion implements Event {
    int begin;
    int end;

    public Deletion(int begin, int end) {
      this.begin = begin;
      this.end = end;
    }
    @Override
    public int begin() {
      return begin;
    }
    @Override
    public int end() {
      return end;
    }
  }

  public EventSeq add(Event event) {
    events.add(event);
    return this;
  }

  public String run() {
    events.sort(Comparator.comparing(Event::begin));
    StringBuilder sb = new StringBuilder();

    int restBegin = 0;
    for (Event event : events) {
      if (restBegin < event.begin()) {
        // Append the unchanged code
        sb.append(source, restBegin, event.begin());
      }

      if (event instanceof Deletion) {
      } else if (event instanceof Insertion) {
        Insertion insertion = (Insertion) event;
        sb.append(insertion.code);
      }

      if (restBegin < event.end()) {
        restBegin = event.end();
      }
    }

    sb.append(source, restBegin, source.length());
    return sb.toString();
  }
}
