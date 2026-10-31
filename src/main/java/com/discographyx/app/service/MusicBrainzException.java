package com.discographyx.app.service;

/** Safe failure information, without external response bodies or request data. */
public class MusicBrainzException extends RuntimeException {

    private final int status;

    public MusicBrainzException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
