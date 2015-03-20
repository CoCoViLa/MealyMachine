# MealyMachine
A CoCoViLa package that enable Mealy machine construction via state diagrams drawing. 
The package is meant to be used together with CoCoViLa (Dynamic or Discrete) 
System Simulator packages.

A state diagram is composed of an Initial State and one or more States. It may have an End State. 
The States are to be connected with Transitions. Transitions are labeled with conditional 
expressions and actions. Based on the conditional expressions it is decided which of the 
Transitions exiting the current State is used on execution. If there are several 
Transitions that are usable (their conditional expressions evaluate to true) the one, 
having alphabetically first name, is used. The action is executed when the Transition is used.
You can find more information on Mealy machine [here](http://en.wikipedia.org/wiki/Mealy_machine). 
A more detailed description of the package can be found [here](https://github.com/CoCoViLa/MealyMachine/wiki).

Usage
------

Typically the Mealy machines are to be used as a part of a bigger (Dynamic or Discrete) System Simulator. For that the Mealy machine schemes must be exported as objects first and then these objects can be included in those Simulation schemes.  

Current CoCoViLa implementation do not support a multitude of packages in a project, hence the content of MealyMachine package should be merged with the content of the Simulator. It is advisable to do as follows:

1. Make a copy of the Simulator package folder (together with all the files and folders in it) and name it as you like (for example MySimulator).
2. Rename the .xml file. It is advisable to rename it to have the same name as the package folder (for example MySimulator.xml).
2. Edit the .xml file (MySimulator.xml) and copy all the classes from the MealyMachine.xml (located in the MealyMachine package folder) into it.
3. Copy all the files (except the MealyMachine.xml) from the MealyMachine package folder into MySimulator package folder.

Now, when you load the MySimulator package in CoCoViLa Scheme Editor you'll get the visual classes for creating Mealy machines together with the classes from the Simulator package.

Creating a Mealy state chart
-----------------------------   

A Mealy state chart in the current implementation should always have one Initial State and one MealyMachineSupervisor block. The latter must be set as super from the right-mouse-click menu.

Introduce as many States as needed and name them properly (name field is accessible from Properties window, which can be opened with a double-mouse-click or a right-mouse-click>Properties).

Introduce the Transitions between the states and define their conditional expressions and actions (again through the Properties window). The condition and action must be entered conforming Javascript syntax. Actions are executed when the Transition condition expression is evaluating to true and the Transition is used.

The conditional expression or a Transition exiting an Initial State is ignored - it is advised to leave it empty. The action is executed before the simulation starts and is typically used to give some initial evaluation to the variables used in the machine.

You can always leave the Transition condition field empty - this is treated as an expression "true". Similarly you can leave empty the action field, which is treated as no action.

When a Mealy machine reaches a Final State - no action is further executed.

Mealy state chart inputs and outputs
-------------------------------------

Typically a Mealy machines use some variables (variables that are referred to in the conditional expressions and actions). These variables have to be declared. In addition the variables that are going to be bound through the ports (connection points to the external world) have to be defined as inputs and/or outputs.

In order to declare the variables and define the inputs and outputs open the editor from Scheme>Extend... and enter something similar to the following:

Use the following lines as an example:
```
// Variables declaration
double in1, in2, out1, out2;
// Input - output definition
alias inputs = [in1, in2];
alias outputs = [out1, out2];
String outputNames = "out1, out2";
```
NB! The variable list in outputs and outputNames must be identical!

Create a new Scheme Object from the drawn scheme
-------------------------------------------------

When you have finished developing the Mealy machine state chart, select all the objects on the scheme and choose File>Export>Scheme>Selection as Object...

In the appearing window give to the scheme a name (and select a suitable icon if you have it already) and click OK.

Now open the visual class in the Class Editor and define its ports (inputs and outputs), create/modify its icon etc. 

Back in Scheme Editor, when you reload the your (MySimulator) package a new icon appears in the toolbar and you can start using it. You can edit the scheme of the machine by making a right-mouse-click on the icon - your scheme is opened in a separate tab. Remember always to save the scheme before execution a new simulation.

Insights
---------

Based on the drawn scheme a program is synthesized for every State. The program looks as an if-else clause that contains all the conditional expressions from the Transitions exiting the State and the actions correspondingly. That program is executed, when the state machine is at the current State, with the Javascript engine. Such an execution corresponds to a state->nextstate axiom realization, that may modify the output variables, bring us to a new or remain in the current state - all depending on the scheme. 
 
Current implementation parses all conditions and actions and extracts variable names that must be passed to the Javascript engine before the execution and read from it after the execution. These variables must be declared, as it is described above.

Troubleshooting
----------------

When on some reason the Mealy machine does not behave as expected or returns an error messages you have an option to turn on some debugging. For that edit the MealyMachine Supervisor code (right-mouse-click>View Code) and set some of the boolean variables starting with "debug" to true. 