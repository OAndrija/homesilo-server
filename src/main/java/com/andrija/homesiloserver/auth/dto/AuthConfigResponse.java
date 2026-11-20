package com.andrija.homesiloserver.auth.dto;

/**
 * What the sign-in pages need to know before anyone signs in.
 *
 * @param registrationEnabled anyone can create an account, without an invite
 * @param setupRequired       nobody has an account yet: the first admin is created with the setup code
 */
public record AuthConfigResponse(boolean registrationEnabled, boolean setupRequired) {}
