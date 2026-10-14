package edu.wgu.pantryprep.service;

/**
 * A group member tried something their role does not allow.
 *
 * <p>The pages never offer such an action, so reaching this means a stale
 * page or a hand-made request. It is answered with 403. Someone who is not in
 * the group at all gets 404 instead, through NoSuchElementException, so a
 * response never confirms that a group they cannot see exists.
 */
public class GroupPermissionException extends RuntimeException {

    public GroupPermissionException(String message) {
        super(message);
    }
}
