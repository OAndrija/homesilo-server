Feature: Invites
  Sign-up is closed on a home server, so an admin sends invite links to the
  people who should have an account. Each link works once.

  Background:
    Given I am signed in as "root", an admin

  Scenario: Someone joins with an invite link
    When I make an invite for "Maja"
    And "maja" signs up with the invite
    Then "maja" can sign in
    And the invite for "Maja" has been used by "maja"

  Scenario: An invite link only works once
    Given I make an invite for "Maja"
    And "maja" signs up with the invite
    When "maks" signs up with the invite
    Then it fails with "This invite link has expired or has already been used. Ask whoever sent it for a new one."

  Scenario: A revoked invite link stops working
    Given I make an invite for "Maja"
    When I revoke the invite for "Maja"
    And "maja" signs up with the invite
    Then it fails with "This invite link has expired or has already been used. Ask whoever sent it for a new one."
