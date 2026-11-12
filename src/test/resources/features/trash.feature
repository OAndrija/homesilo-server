Feature: Trash
  Deleting goes through the trash first, so mistakes can be undone.

  Background:
    Given I am signed in as "ana"

  Scenario: A trashed file can be restored
    Given I have uploaded "notes.txt" containing "Buy milk"
    When I move "notes.txt" to the trash
    Then My Files does not contain "notes.txt"
    And the trash contains "notes.txt"
    When I restore "notes.txt"
    Then My Files contains "notes.txt"
    And the trash is empty

  Scenario: Trashing a folder takes everything inside with it
    Given I have a folder "Archive"
    And I have uploaded "old.txt" containing "old news" into "Archive"
    When I move "Archive" to the trash
    Then My Files is empty
    And the trash contains "Archive"
    When I restore "Archive"
    Then the folder "Archive" contains "old.txt"

  Scenario: A file has to be in the trash before it can be deleted forever
    Given I have uploaded "keep.txt" containing "keep me"
    When I delete "keep.txt" forever
    Then it fails with "Move the file to trash before deleting it permanently"
    And My Files contains "keep.txt"
