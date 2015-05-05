import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import ee.ioc.cs.vsle.api.ProgramContext;
import ee.ioc.cs.vsle.api.SchemeObject;
import ee.ioc.cs.vsle.editor.SchemeContainer;
import ee.ioc.cs.vsle.vclass.RelObj;
import ee.ioc.cs.vsle.vclass.Scheme;

public class MealyMachineSupervisor {
    /*@ specification MealyMachineSupervisor  {
      String initstate, state, nextstate, finalstate;

      alias inputs;
      alias outputs;
      String outputNames;

      cocovilaSpecObjectName -> initstate {init};
      state, inputs, cocovilaSpecObjectName -> nextstate {advance};
      nextstate, outputNames -> outputs {setInterfaceVariables};

   } @*/

    boolean debugContext = true;
    boolean debugParse = true;
    boolean debugProgGen = false;
    boolean debugExecute = true;
    boolean debug = debugContext || debugParse || debugProgGen || debugExecute;

    Map <String,String> stateCodes = new HashMap<String,String>();
    Map <String,HashSet<String>> stateCodeInputs = new HashMap<String,HashSet<String>>();
    Map <String,HashSet<String>> stateCodeOutputs = new HashMap<String,HashSet<String>>();
    Map <String,ArrayList<SubtaskMethod>> stateSubtasks = new HashMap<String,ArrayList<SubtaskMethod>>();

    public String init(String csoName) {
        if (debug) {
            System.out.println("\nMealyMachineSupervisor "+csoName+" init");
        }
        if (debugContext) {
            System.out.println("  context: class - "+this.getClass().getName());
            System.out.println("  context: fields in class - ");
            for (Field field : this.getClass().getDeclaredFields()) {
                System.out.println("    "+field.getType().getName()+" : "+field.getName());
            }
        }
        List<String> initStatesAL = new ArrayList<String>();
        List<String> statesAL = new ArrayList<String>();
        List<SchemeObject> endStatesAL = new ArrayList<SchemeObject>();
        List<SchemeObject> transitionsAL = new ArrayList<SchemeObject>();
        Map <String,ArrayList<RelObj>> statesTrans = new HashMap<String,ArrayList<RelObj>>();

        // Locate the package directory
        Scheme topScheme = (Scheme) ProgramContext.getScheme();
        String dir = topScheme.getContainer().getWorkDir();
        // Find out the name of the current scheme 
        String className = "";
        if (debugContext) {
            System.out.println("  Objects in top level scheme");
        }
        for ( SchemeObject schObj : topScheme.getObjects() ) {
            if (debugContext) {
                System.out.println("    "+schObj.getClassName()+" : \""+schObj.getName()+"\"");
            }
            if (schObj.getName().equals(csoName)) {
                className = schObj.getClassName();
                break;
            }
        }

        // Load the scheme
        File file = new File(dir, className + ".syn");
        SchemeContainer sc = new SchemeContainer(topScheme.getPackage(), dir);
        sc.loadScheme(file);

        // Collect states and transitions
        if (debugContext) {
            System.out.println("  Process objects from the current scheme");
        }
        for ( SchemeObject schObj : sc.getScheme().getObjects() ) {
            if (debugContext) {
                System.out.println("    "+schObj.getClassName()+" : "+schObj.getName());
            }
            switch (schObj.getClassName()) {
            case "InitState": initStatesAL.add(schObj.getName()); statesAL.add(schObj.getName()); break;
            case "State": statesAL.add(schObj.getName()); break;
            case "EndState": endStatesAL.add(schObj); break;
            case "Transition": transitionsAL.add(schObj);
            // Store the transition at the State it exits in the collection of States
            RelObj transition = (RelObj) schObj;
            SchemeObject fromStateSchObj = getFromObj(transition);
            String fromStateObjName = fromStateSchObj.getName();
            String fromStateName = "";
            // Init- and EndStates have no name, so remember the names of only States for debugging
            if (fromStateSchObj.getClassName()=="State") {
                fromStateName = (String) fromStateSchObj.getFieldValue("name");
            }
            if (debugContext) {
                System.out.println(
                        "      " + fromStateObjName+"--"+fromStateName+" -> "
                                + getToObj(transition).getName()
                                + "\n        " + schObj.getFieldValue("condition")
                                + "\n        "+schObj.getFieldValue("action"));
            }
            // Add a new state to the collection when needed
            if (!statesTrans.containsKey(fromStateObjName)) {
                statesTrans.put(fromStateObjName, new ArrayList<RelObj>());
            }
            // Add the transition at the State
            statesTrans.get(fromStateObjName).add(transition);
            }
        }
        if (initStatesAL.size()==0) {
            System.err.println("InitState not present @ "+csoName);
            return null;
        } else if (initStatesAL.size()>1) {
            System.err.println("Several InitStates is not allowed @ "+csoName);
            return null;
        }

        // Create condition and action code pieces - programs to be executed from the states
        Parser parser = new Parser(topScheme, debugParse);
        for (String state : statesAL) {
            if (debugProgGen) {
                System.out.println("\n Creating program for state "+state);
            }
            // Sort transitions based on the "order" field
            ArrayList<RelObj> ts = statesTrans.get(state);
            if (ts == null) {
                System.err.println("A state without exiting transitions should be finite.");
                return null;
            }
            if (debugProgGen) {
                System.out.println(" Transitions before sort "+ts);
            }
            boolean swapped = true;
            while (swapped) {
                swapped = false;
                for (int i=0; i<ts.size()-1; i++) {
                    String tsoCurrent = (String)ts.get(i).getFieldValue("order"); 
                    String tsoNext = (String)ts.get(i+1).getFieldValue("order");
                    int tsoC = 0, tsoN = 0;
                    if (tsoCurrent != null) {
                        tsoC = Integer.valueOf(tsoCurrent);
                    }
                    if (tsoNext != null) {
                        tsoN = Integer.valueOf(tsoNext);
                    }
                    if (tsoC > tsoN) {
                        RelObj cache = ts.get(i);
                        ts.set(i, ts.get(i+1));
                        ts.set(i+1, cache);
                        swapped = true;
                    }
                }
            }
            if (debugProgGen) {
                System.out.println(" Transitions after sort "+ts);
            }
            // Create the code
            String body = "";
            HashSet<String> inputs = new HashSet<String>();
            HashSet<String> outputs = new HashSet<String>();
            ArrayList<SubtaskMethod> subtasks = new ArrayList<SubtaskMethod>();
            if (initStatesAL.contains(state)) {
                RelObj trans = statesTrans.get(state).get(0);
                String action = processAction(trans.getField("action").getValue(), 
                        inputs, outputs, subtasks, parser); 
                body += action;
                body += "   nextState = \""+getToObj(trans).getName()+"\";";
            } else {
                for (RelObj trans : statesTrans.get(state)) {
                    // Extract lists of input-output variables
                    String condition = processCondition(
                            trans.getField("condition").getValue(), inputs, subtasks, parser);
                    String action = processAction(trans.getField("action").getValue(), 
                            inputs, outputs, subtasks, parser); 

                    // Add an if-else statement
                    body += "if ("+condition+") {\n";
                    body += action;
                    body += "   nextState = \""+getToObj(trans).getName()+"\";\n";
                    body += "} else ";
                }
                body += "{\n   print(\"WARNING - No proper exit from state " + state+"\");\n}";
            }
            // Store the code and relevant stuff
            stateCodes.put(state, body);
            stateCodeInputs.put(state, inputs);
            stateCodeOutputs.put(state, outputs);
            stateSubtasks.put(state, subtasks);

            if (debugProgGen) {
                System.out.println(" -> "+Arrays.asList(inputs));
                System.out.println(body);
                System.out.println(" <- "+Arrays.asList(outputs));
            }
        }

        // Perform actions from initState to first state
        String firstState = advance(initStatesAL.get(0), null, csoName);

        // Return initiated objects
        return firstState;
    }

    public String advance(String state, Object[] inputs, String csoName) {
        if (debug) {
            System.out.println("\nMealyMachineSupervisor "+csoName+"@"+state+" advance");
        }
        String nextState = null;
        try {
            String code = stateCodes.get(state);
            if (code != null) {
                ScriptEngineManager sem = new ScriptEngineManager();
                ScriptEngine js = sem.getEngineByName("ECMAScript");
                ScriptContext ctx = js.getContext();

                if (debugExecute) {
                    System.out.println("  Source variables: "+stateCodeInputs.get(state));
                }
                Class<? extends MealyMachineSupervisor> cl = this.getClass();
                for (String input : stateCodeInputs.get(state)) {
                    Field f = cl.getDeclaredField(input);
                    if (debugExecute) {
                        System.out.print(input+" = "+f.get(this)+",  ");
                    }
                    ctx.setAttribute(input, f.get(this), ScriptContext.ENGINE_SCOPE);
                }
                ArrayList<SubtaskMethod> subtasks = stateSubtasks.get(state);
                if (subtasks!=null &&!subtasks.isEmpty()) {
                    ctx.setAttribute("stms", subtasks.toArray(), ScriptContext.ENGINE_SCOPE);
                }

                if (debugExecute) {
                    System.out.println("\n  Executing:\n"+code);
                }
                js.eval(code);

                if (debugExecute) {
                    System.out.println("  Modified variables: "+stateCodeOutputs.get(state));
                }
                for (String output : stateCodeOutputs.get(state)) {
                    Field f = cl.getDeclaredField(output);
                    f.set(this, js.get(output));
                    if (debugExecute) {
                        System.out.print(output+" = "+f.get(this)+",  ");
                    }
                }
                if (debugExecute) {
                    System.out.println();
                }
                nextState = (String) js.get("nextState");
            } else {
                if (debugExecute) {
                    System.out.println("  No code!");
                }
                return state;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return nextState;
    }

    private SchemeObject getFromObj(RelObj transition) {
        return transition.getStartPort().getObject();
    }
    private SchemeObject getToObj(RelObj transition) {
        return transition.getEndPort().getObject();
    }

    /**
     * Parse the condition expression of the transition.
     * 
     * NB! Subtasks are not allowed for now.
     * 
     * @param logExp
     * @param inputs
     * @param subtasks
     * @return
     */
    private String processCondition(String logExp, HashSet<String> inputs, 
            List<SubtaskMethod> subtasks, Parser parser) {
        if (debugParse) {
            System.out.println("Parsing condition "+logExp);
        }
        if (logExp == null ||logExp.isEmpty() || logExp.equals("true")) {
            return "true";
        }
        String result = parser.parseExpression(logExp.replaceAll("\\s+", ""), 
                inputs, null, subtasks);
        if (debugParse) {
            System.out.println(" Inputs extracted: "+inputs);
            System.out.println(" Subtasks handled: "+subtasks);
            System.out.println(" Return condition: "+result);
        }
        return result;
    }

    /**
     * Parse the action statements
     * 
     * @param clauses
     * @param inputs
     * @param outputs
     * @param subtasks
     * @return
     */
    private String processAction(String clauses, HashSet<String> inputs, 
            HashSet<String> outputs, List<SubtaskMethod> subtasks, Parser parser) {
        if (debugParse) {
            System.out.println("Parsing action:\n{"+clauses+"\n}");
        }
        if (clauses==null || clauses.isEmpty()) {
            return "";
        }
        String result = "";
        for (String clause : clauses.split(";")) {
            result += parser.parseStatement(clause, inputs, outputs, subtasks);
        }
        if (debugParse) {
            System.out.println(" Inputs extracted: "+inputs);
            System.out.println(" Outputs extracted: "+outputs);
            System.out.println(" Subtasks handled: "+subtasks);
            System.out.println(" Return action:\n"+result);
        }
        return result;
    }

    /**
     * A method used to set the alias values based on a given filed name list
     * @param state
     * @param nameList
     * @return
     */
    public Object[] setInterfaceVariables(String state, String nameList) {
        String[] names = nameList.split(",");
        Object[] result = new Object[names.length];
        try {
            for (int i=0; i<names.length; i++) {
                result[i] = this.getClass().getDeclaredField(names[i]).get(this);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return result;
    }

}
