package edu.wgu.pantryprep.service;

/**
 * An allowed action that cannot go ahead right now, such as joining a full
 * group. The message is written for the cook and shown as-is.
 */
public class GroupRuleException extends RuntimeException {

    public GroupRuleException(String message) {
        super(message);
    }
}
