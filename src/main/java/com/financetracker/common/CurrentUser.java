package com.financetracker.common;

/**
 * Who is making the request. Every query is scoped by this id, so users can never see each
 * other's data. The implementation reads the user id from the validated JWT.
 */
public interface CurrentUser {

    Long id();
}
