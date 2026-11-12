Feature: Dashboard layout
  People choose which tiles their dashboard shows, in what order and at what size.
  The layout is kept with their account, so it follows them to every device.

  Background:
    Given I am signed in as "ana"

  Scenario: A new account starts with the default dashboard
    Then my dashboard is the default one

  Scenario: My tiles are kept in the order and size I chose
    When I arrange my dashboard as:
      | tile        | size  |
      | recent      | wide  |
      | storage     | small |
      | total-files | small |
    Then my dashboard shows:
      | tile        | size  |
      | recent      | wide  |
      | storage     | small |
      | total-files | small |

  Scenario: Someone else's dashboard is not affected
    When I arrange my dashboard as:
      | tile    | size |
      | starred | wide |
    And I am signed in as "ben"
    Then my dashboard is the default one

  Scenario: Resetting brings back the default
    Given I arrange my dashboard as:
      | tile    | size  |
      | storage | large |
    When I reset my dashboard
    Then my dashboard is the default one

  Scenario: A tile can only be on the dashboard once
    When I arrange my dashboard as:
      | tile    | size  |
      | storage | small |
      | storage | wide  |
    Then it fails with "Each tile can only be on the dashboard once"
