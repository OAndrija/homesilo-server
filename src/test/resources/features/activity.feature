Feature: Activity log
  Admins can see who did what on the server, and everyone can check what
  happened to their own account, like sign-ins they don't recognise.

  Scenario: An admin sees what someone did with their files
    Given I am signed in as "root", an admin
    And "maja" has uploaded "holiday.jpg" containing "sunny"
    When I look at the activity of "maja"
    Then the latest entry says "maja" uploaded "holiday.jpg"

  Scenario: Admin changes to an account show up in its history
    Given I am signed in as "root", an admin
    And "maja" has an account
    When I disable the account of "maja"
    And I look at the activity of "maja"
    Then the latest entry says "root" disabled "maja"

  Scenario: People see failed sign-ins on their own account
    Given I am signed in as "maja"
    When someone tries to sign in as "maja" with a wrong password 2 times
    Then my recent activity starts with 2 failed sign-ins
