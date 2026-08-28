package com.pickagent.w1d5.core;

import java.util.List;
import java.util.Objects;

public record EventData(String name, String date, List<String> participants) {
    public EventData {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(date, "date");
        participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
    }
}
