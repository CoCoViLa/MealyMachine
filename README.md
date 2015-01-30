# MealyMachine
A CoCoViLa package that enable Mealy machine construction via state charts drawing. The package is meant to be used together with CoCoViLa (Dynamic or Discrete) System Simulator packages.

A state chart is composed of an Initial State and one or more States. It may have an End State. The States are to be connected with Transitions. Transitions are labeled with conditional expressions and actions. Based on the conditional expressions it is decided which of the Transition exiting the current State is used. If there are several Transitions that are usable (their conditional expressions evaluate to true) the one, having alphabetically first name, is used. The action is executed when the Transition is used.

Usage
------

Typically the Mealy machines are to be used as a part of a bigger (Dynamic or Discrete) System Simulation. For that the Mealy machine schemes must be exported as objects first and then these objects can be included in those System Simulation schemes.  

Current CoCoViLa implementation do not support a multitude of packages in a project, hence the content of MealyMachine package should be merged with the content of the Simulator. It is advisable to do as follows:
1. Make a copy of the Simulator package folder (together with all the files and folders in it) and name it as you like (for example MySimulator).
2. Rename the .xml file. It is advisable to rename it with the same name as the package folder (for example MySimulator.xml).
2. Edit the .xml file (MySimulator.xml) and copy all the classes from the MealyMachine.xml into it.
3. Copy all the files (except the MealyMachine.xml) from the MealyMachine package into MySimulator package folder.

Now, when you load the MySimulator package in CoCoViLa Scheme Editor you'll get the the visual classes for creating Mealy machines together with the classes from the Simulator package.

Creating a Mealy state chart
-----------------------------   

A Mealy state chart in the current implementation should always have one Initial State and one MealyMachineSupervisor block. The latter must be set as super from its properties.

Mealy state chart inputs and outputs
-------------------------------------

Define the inputs and outputs of the scheme at Scheme>Extend...

Use the following lines as an example:
alias inputs = [in1, in2];
alias outputs = [out1, out2];
String outputNames = "out1, out2";

Create a new Scheme Object from the drawn scheme
-------------------------------------------------

Select all the objects on the Scheme and choose File>Export>Scheme>Selection as Object...
In the appearing window give a name to the Scheme and click OK.
Now open the visual class in the Class Editor and define its ports (inputs and outputs), create an icon etc.