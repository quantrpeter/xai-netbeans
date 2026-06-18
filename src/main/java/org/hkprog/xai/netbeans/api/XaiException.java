package org.hkprog.xai.netbeans.api;

/** Raised when a request to the xAI API fails. */
public class XaiException extends Exception {

    public XaiException(String message) {
        super(message);
    }

    public XaiException(String message, Throwable cause) {
        super(message, cause);
    }
}
