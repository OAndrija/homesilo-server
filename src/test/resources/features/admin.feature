Feature: Managing users
  Admins look after the accounts on their server: they add people, switch
  accounts off and on, and remove accounts that are no longer needed.

  Background:
    Given I am signed in as "root", an admin

  Scenario: An admin can add someone, who can then sign in
    When I add a user "lea"
    Then "lea" can sign in with the password I gave them

  Scenario: A disabled account can't sign in until it is enabled again
    Given "maja" has an account
    When I disable the account of "maja"
    Then "maja" can't sign in because "This account has been disabled"
    When I enable the account of "maja"
    Then "maja" can sign in

  Scenario: Deleting an account removes it with its files
    Given "maja" has uploaded "holiday.jpg" containing "sunny"
    When I delete the account of "maja"
    Then the user list doesn't include "maja"

  Scenario: Admins can't lock themselves out
    When I try to disable my own account
    Then it fails with "You can't disable your own account"

  Scenario: Only admins can see the user list
    Given I am signed in as "maja"
    When I open the user list
    Then it fails with "You do not have permission"
