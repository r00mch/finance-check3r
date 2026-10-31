package com.financetracker.common;

/**
 * Who is making the request. Every query is scoped by this id, so users can never see each
 * other's data. Stage 2 uses a fixed demo user; stage 3 replaces it with the authenticated one.
 */
public interface CurrentUser {

    Long id();
}
