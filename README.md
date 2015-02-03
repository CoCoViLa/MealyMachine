# MealyMachine
A CoCoViLa package that enable Mealy machine construction via state charts drawing. The package is meant to be used together with CoCoViLa (Dynamic or Discrete) System Simulator packages.

A state chart is composed of an Initial State and one or more States. It may have an End State. The States are to be connected with Transitions. Transitions are labeled with conditional expressions and actions. Based on the conditional expressions it is decided which of the Transitions exiting the current State is used on scheme execution. If there are several Transitions that are usable (their conditional expressions evaluate to true) the one, having alphabetically first name, is used. The action is executed when the Transition is used.

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

A Mealy state chart in the current implementation should always have one Initial State and one MealyMachineSupervisor block. The latter must be set as super from its properties.

Introduce as many States as neccessary and give to them proper names (from the Object Preferences).

Introduce the Transitions between the states and define their conditional expressions and actions. Actions are executed when the Transition condition expression is evaluating to true and the Transition is used. The condition and action must be entered conforming Javascript syntax.  

Mealy state chart inputs and outputs
-------------------------------------

Typically a Mealy machine scheme use some variables (variables that are referred to in the conditional expressions and actions). These variables have to be declared. In addition the variables that are going to be bound through the ports have to be defined as inputs and/or outputs.

In order to declare the variables and define the inputs and outputs of the scheme open the editor from Scheme>Extend... and enter something similar to the following:

Use the following lines as an example:
```
// Variables declaration
double in1, in2, out1, out2;
// Input - output definition
alias inputs = [in1, in2];
alias outputs = [out1, out2];
String outputNames = "out1, out2";
```

Create a new Scheme Object from the drawn scheme
-------------------------------------------------

When you have finished developing the Mealy machine state chart, select all the objects on the Scheme and choose File>Export>Scheme>Selection as Object...
In the appearing window give a name to the Scheme and click OK.
Now open the visual class in the Class Editor and define its ports (inputs and outputs), create an icon etc. When you reload the your (MySimulator) package a new icon appears in the toolbar and you can start using it. You can edit the scheme of the machine by making a right-mouse-click on the icon - you scheme is opened in the separated tab. Remember always to save the scheme before execution a new simulation. 