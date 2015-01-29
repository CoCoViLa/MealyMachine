# MealyMachine
A CoCoViLa package that allows drawing Mealy machine state charts for System Simulators.

A state chart is composed of Initial State and one or more States. It may have an End State. The states are to be connected with Transitions. The Transitions are driven by conditional expressions based on which it is decided which of then is taken. When there are several conditions exiting the current State that can be used by the conditional expression the one that falls first after sorting them by name is used.

Every Transition hava an action field that is executed when the Transition is used.

A state chart in the current implementation should have always one Initial State and a MealyMachineSupervisor block. The latter must be set as super from the preferences menu.

Usage
------

Typically the Mealy machines are to be used as a part of a bigger (Dynamic or Discrete) System Simulation. For that the Mealy machine schemes must be exported as objects first and then these objects can be included in those System Simulation schemes.  

