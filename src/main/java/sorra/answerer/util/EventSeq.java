package sorra.answerer.util;

import java.util.ArrayList;
import java.util.List;

public class EventSeq {
  private String source;
  private List<Event> events = new ArrayList<>();

  public static interface Event {}

  public static class Insertion {
    String code;
    int nextIndex;
  }

  public static class Deletion {
    int begin;
    int end;
  }
}
