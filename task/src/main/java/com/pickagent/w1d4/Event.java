package com.pickagent.w1d4;

import java.util.List;
import java.util.Objects;

public record Event(String name, String date, List<String> participants) {
    public Event {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(date, "date");
        participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
    }
}
