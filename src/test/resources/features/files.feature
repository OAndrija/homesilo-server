Feature: Files
  Files go up and come back down exactly as they were, stay private to
  their owner, and can be sorted into folders.

  Background:
    Given I am signed in as "ana"

  Scenario: An uploaded file downloads unchanged
    When I upload "notes.txt" containing "Buy milk"
    Then My Files contains "notes.txt"
    And downloading "notes.txt" gives "Buy milk"

  Scenario: Folders keep files together
    Given I have a folder "Photos"
    When I upload "beach.jpg" containing "sand and sea" into "Photos"
    Then the folder "Photos" contains "beach.jpg"
    And My Files does not contain "beach.jpg"

  Scenario: Other people can't open my files
    Given I have uploaded "diary.txt" containing "Dear diary"
    Then "bojan" can't open my file "diary.txt"

  Scenario Outline: A rename can change the name but not the file type
    Given I have uploaded "<file>" containing "some data"
    When I rename "<file>" to "<new name>"
    Then the rename is <result>

    Examples:
      | file       | new name    | result  |
      | report.pdf | summary.pdf | allowed |
      | report.pdf | summary.PDF | allowed |
      | report.pdf | summary.i   | refused |
      | report.pdf | summary     | refused |
      | README     | README.md   | allowed |
