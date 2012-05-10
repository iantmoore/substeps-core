Feature: table as a parameter Feature


Tags: table_as_a_parameter
Scenario: A scenario that passes a table as a parameter through to a substep
	Given a step that takes a table
          | param1  |  param2  |    param3  |   param4  |
          | W       |   X      |    Y       |     Z     |       
    And a normal step        