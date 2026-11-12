Feature: Storage limit
  Every account has a storage limit. Files in the trash still count
  toward it until the trash is emptied.

  Background:
    Given I am signed in as "ana"
    And my storage limit is 20 bytes

  Scenario: An upload that doesn't fit is refused
    Given I have uploaded "first.txt" containing "0123456789"
    When I upload "second.txt" containing "this is too much text"
    Then it fails with "Not enough storage space left for 'second.txt'"
    And My Files does not contain "second.txt"

  Scenario: Emptying the trash frees up space
    Given I have uploaded "first.txt" containing "0123456789"
    And I have moved "first.txt" to the trash
    Then I am using 10 bytes of storage
    When I upload "second.txt" containing "0123456789ab"
    Then it fails with "Not enough storage space left"
    When I empty the trash
    Then I am using 0 bytes of storage
    When I upload "second.txt" containing "0123456789ab"
    Then My Files contains "second.txt"
