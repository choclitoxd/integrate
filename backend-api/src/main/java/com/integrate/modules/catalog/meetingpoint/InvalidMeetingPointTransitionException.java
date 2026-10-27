package com.integrate.modules.catalog.meetingpoint;

/** El par estado × evento no está en la tabla del autómata (ADR-0030 §1b): lo no listado se rechaza. */
public class InvalidMeetingPointTransitionException extends RuntimeException {

    private final MeetingPointState from;
    private final MeetingPointEvent event;

    public InvalidMeetingPointTransitionException(MeetingPointState from, MeetingPointEvent event) {
        super("No transition from " + from + " on " + event);
        this.from = from;
        this.event = event;
    }

    public MeetingPointState from() {
        return from;
    }

    public MeetingPointEvent event() {
        return event;
    }
}
