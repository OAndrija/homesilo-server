Feature: Accounts
  Everyone gets their own silo. Signing up signs you in, each username
  belongs to one person, and guessing passwords gets slowed down.

  Scenario: Signing up signs you straight in
    When I sign up as "maja"
    Then I am signed in to my own account

  Scenario: A username can only be taken once
    Given "maja" has an account
    When someone else signs up as "maja"
    Then it fails with "Username is already taken"

  Scenario: Sign-in pauses after too many wrong passwords
    Given "maja" has an account
    When someone tries to sign in as "maja" with a wrong password 3 times
    Then "maja" can't sign in for a while, even with the right password
