Feature: Preferences
  People choose how the app looks and how their lists are shown. The choices are
  kept with their account, so they follow it to every device and stay separate
  from anyone else who signs in on the same browser.

  Background:
    Given I am signed in as "ana"

  Scenario: A new account starts with the defaults
    Then I have no changed settings

  Scenario: My choices are kept
    When I change my settings:
      | setting            | value   |
      | theme.mode         | dark    |
      | appearance.spacing | compact |
      | view.starred       | grid    |
    Then my settings are:
      | setting            | value   |
      | theme.mode         | dark    |
      | appearance.spacing | compact |
      | view.starred       | grid    |

  Scenario: Someone else signing in keeps their own look
    When I change my settings:
      | setting            | value   |
      | appearance.spacing | compact |
    And I am signed in as "ben"
    Then I have no changed settings

  Scenario: A setting can go back to its default
    Given I change my settings:
      | setting           | value |
      | appearance.cards  | flat  |
      | appearance.motion | full  |
    When I reset the setting "appearance.cards"
    Then my settings are:
      | setting           | value |
      | appearance.motion | full  |

  Scenario: Only real settings and values are kept
    When I change my settings:
      | setting            | value |
      | appearance.spacing | huge  |
    Then it fails with "Spacing must be compact, comfortable or spacious"
