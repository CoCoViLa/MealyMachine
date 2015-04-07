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
      double timeStep; // length of a simulation step in time units

      String initstate, state, nextstate, finalstate;

      alias inputs;
      alias outputs;
      String outputNames;

      cocovilaSpecObjectName -> initstate {init};
      state, inputs, cocovilaSpecObjectName -> nextstate {advance};
      nextstate, outputNames -> outputs {setInterfaceVariables};

   } @*/

   boolean debugContext = false;
   boolean debugParse = false;
   boolean debugProgGen = false;
   boolean debugExecute = false;
   boolean debug = debugContext || debugParse || debugProgGen || debugExecute;

   Map <String,String> stateCodes = new HashMap<String,String>();
   Map <String,HashSet<String>> stateCodeInputs = new HashMap<String,HashSet<String>>();
   Map <String,HashSet<String>> stateCodeOutputs = new HashMap<String,HashSet<String>>();
   Map <String,ArrayList<SubtaskMethod>> stateSubtasks = new HashMap<String,ArrayList<SubtaskMethod>>();
   
   static String[] arithmOp = new String {"+","-","*","/","%"};
   static String[] comparOp = new String {"==","!=","<",">","<=",">="};
   static String[] logicOp = new String {"||","&&","!","&","|","~","^","<<",">>",""};
   static String[] evalOp = new String {"="};
   static String[] complexEvalOp = new String {"+=","-=","*=","/=","%="};
   static String[] unaryEvalOp = new String {"++","--"};

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

      //FIXME Mealy schemes are typically subschemes!
      Scheme topScheme = (Scheme) ProgramContext.getScheme();
      String dir = topScheme.getContainer().getWorkDir();
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

      File file = new File(dir, className + ".syn");
      SchemeContainer sc = new SchemeContainer(topScheme.getPackage(), dir);
      sc.loadScheme(file);

      //GObj thisGObj = (GObj)scheme.getObject(name);
      // Collect transitions exiting states
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
         // Create condition and action code pieces for later usage
         RelObj transition = (RelObj) schObj;
         SchemeObject fromStateSchObj = getFromObj(transition);
         String fromStateObjName = fromStateSchObj.getName();
         String fromStateName = "";
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
         if (!statesTrans.containsKey(fromStateObjName)) {
            statesTrans.put(fromStateObjName, new ArrayList<RelObj>());
         }
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

      // Create programs to be executed from the states
      for (String state : statesAL) {
         if (debugProgGen) {
            System.out.println("\n Creating program for state "+state);
         }
         // Sort transitions
         ArrayList<RelObj> ts = statesTrans.get(state);
         if (ts == null) {
            System.err.println("A state without exiting transitions should be finite.");
            return null;
         }
         if (debugProgGen) {
            System.out.println(" Transitions before sort "+Arrays.asList(ts));
         }
         boolean swapped = true;
         while (swapped) {
            swapped = false;
            for (int i=0; i<ts.size()-1; i++) {
               // FIXME probably faulty comparison when more than 10 transitions are in place
               if (ts.get(i).getName().compareTo(ts.get(i+1).getName())>0) {
                  RelObj cache = ts.get(i);
                  ts.set(i, ts.get(i+1));
                  ts.set(i+1, cache);
                  swapped = true;
               }
            }
         }
         if (debugProgGen) {
            System.out.println(" Transitions after sort "+Arrays.asList(ts));
         }

         String body = "";
         HashSet<String> inputs = new HashSet<String>();
         HashSet<String> outputs = new HashSet<String>();
         ArrayList<SubtaskMethod> subtasks = new ArrayList<SubtaskMethod>();
         if (initStatesAL.contains(state)) {
            RelObj trans = statesTrans.get(state).get(0);
            String action = processAction(
                  trans.getField("action").getValue(), inputs, outputs, subtasks); 
            body += action;
            body += "   nextState = \""+getToObj(trans).getName()+"\";";
         } else {
            for (RelObj trans : statesTrans.get(state)) {
               // Extract lists of input-output variables
               String condition = processCondition(
                     trans.getField("condition").getValue(), inputs, subtasks);
               String action = processAction(
                     trans.getField("action").getValue(), inputs, outputs, subtasks); 

               // Add an if-else statement
               body += "if ("+condition+") {\n" + action;
               body += "   nextState = \""+getToObj(trans).getName()+"\";";
               body += "\n} else ";
            }
            /*
				body += "{\n\t System.out.println(\"No proper exit from state "
						+ state+"\");\n}";
             */
            body += "{\n   print(\"WARNING - No proper exit from state "
                  + state+"\");\n}";
         }

         /*
			String source = "";
			// Add class heading
			// source += "class "+stateName+"_Processor implements Subtask {\n";
			// source += "\tpublic Object[] run(Object[] input) {\n";
			source += "// Input declaration and initialisation\n";
			source += "\n";
			source += "// Body\n";
			source += body;
			source += "\n";
			source += "// Output encapsulation\n";
			source += "\n";
			source += "return nextState;\n";
			// source += "}\n";
          */

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
         List<SubtaskMethod> subtasks) {
      if (debugParse) {
         System.out.println("Parsing condition "+logExp);
      }
      if (logExp == null ||logExp.isEmpty() || logExp.equals("true")) {
         return "true";
      }
      String[] split1 = logExp.split("<|>|=|\\+|\\-|\\*|/|\\(|\\)|\\|\\||&&|!");
      for (String s : split1) {
         if (debugParse) {
            System.out.println(" Token extracted: "+s);
         }
         if (s.length()>0 && !s.matches("\\d*")) { // has some size and is not numeric
            inputs.add(s.trim());
         }
      }
      String result = logExp;
      if (debugParse) {
         System.out.println(" Inputs extracted: "+Arrays.asList(inputs));
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
         HashSet<String> outputs, List<SubtaskMethod> subtasks) {
      if (debugParse) {
         System.out.println("Parsing action:\n{"+clauses+"\n}");
      }
      if (clauses==null || clauses.isEmpty()) {
         return "";
      }
      String result = "";
      for (String clause : clauses.split(";")) {
         if (clause.matches(".+\\+=.+")) {
            if (debugParse) {
               System.out.println(" case (+=): "+clause);
            }
            String[] cSplit = clause.split("\\+=");
            String outVar = cSplit[0].trim();
            inputs.add(outVar);
            outputs.add(outVar);
            if (debugParse) {
               System.out.println("  outputs extracted: "+outVar);
            }
            String s = parseExpression(cSplit[1], inputs, subtasks);
            result += "   "+outVar + "+=" + s + ";\n";
         } else if (clause.matches(".+\\-=.+")) {
            if (debugParse) {
               System.out.println(" case (-=): "+clause);
            }
            String[] cSplit = clause.split("\\-=");
            String outVar = cSplit[0].trim();
            inputs.add(outVar);
            outputs.add(outVar);
            if (debugParse) {
               System.out.println("  outputs extracted: "+outVar);
            }
            String s = parseExpression(cSplit[1], inputs, subtasks);
            result += "   "+outVar + "-=" + s + ";\n";
         } else if (clause.matches(".+\\+\\+")) {
            if (debugParse) {
               System.out.println(" case (++): "+clause);
            }
            String[] cSplit = clause.split("\\+\\+");
            String outVar = cSplit[0].trim();
            inputs.add(outVar);
            outputs.add(outVar);
            if (debugParse) {
               System.out.println("  outputs extracted: "+outVar);
            }
            result += "   "+outVar + "++;\n";
         } else if (clause.matches(".+\\-\\-")) {
            if (debugParse) {
               System.out.println(" case (--): "+clause);
            }
            String[] cSplit = clause.split("\\-\\-");
            String outVar = cSplit[0].trim();
            inputs.add(outVar);
            outputs.add(outVar);
            if (debugParse) {
               System.out.println("  outputs extracted: "+outVar);
            }
            result += "   "+outVar + "--;\n";
         } else if (clause.matches(".+\\=\\[.+\\|\\-.+\\-\\>.+\\]\\(.+\\)")) {
            // [out1,out2] = [Model|-x,y->z](in1,in2)
            if (debugParse) {
               System.out.println(" case (ST): "+clause);
            }
            SubtaskMethod stm = new SubtaskMethod();
            subtasks.add(stm);
            stm.myIndex = subtasks.indexOf(stm);
            ArrayList<String> outVars = new ArrayList<String>();
            // Extract and parse the outputs part of the equation
            String outputNames = clause.substring(0, clause.indexOf("=")).trim();
            if (outputNames.matches("\\[.+\\]")) { // several outputs
               String head = clause.substring(0, clause.indexOf("=")-1);
               stm.outputNames = head.substring(head.indexOf("[")+1, head.lastIndexOf("]")-1).split(",");
            } else { // one output
               outVars.add(outputNames);
               stm.outputNames = new String[] {outputNames};
            }
            inputs.addAll(outVars);
            outputs.addAll(outVars);
            if (debugParse) {
               System.out.println("   outputNames: "+Arrays.toString(stm.outputNames));
            }

            // Extract and parse the specification part of the clause
            // Because the head part may contain additional square brackets take the tail first
            String tail = clause.substring(clause.indexOf("=")+1);
            stm.prepare(ProgramContext.getScheme(), tail.substring(0, tail.indexOf("]")+1));
            
            // Extract and parse the inputs part of the clause
            stm.inputNames = clause.substring(clause.indexOf("(")+1, clause.lastIndexOf(")")).split(",");
            if (debugParse) {
               System.out.println("   inputNames: "+Arrays.toString(stm.inputNames));
            }

            if (debugParse) {
               System.out.println("  "+stm);
            }
            result += stm.getJavaScript();
         } else {
            String[] cSplit = clause.split("=",2);
            if (cSplit.length==1) { // There was no "=" in clause
               if (debugParse) {
                  System.out.println(" case (MC): "+clause);
               }
               String s = parseExpression(cSplit[0], inputs, subtasks);
               result += "   "+s + ";\n";
            } else if (cSplit.length==2) {
               if (debugParse) {
                  System.out.println(" case (=): "+clause);
               }
               String outVar = cSplit[0].trim();
               inputs.add(outVar);
               outputs.add(outVar);
               String s = parseExpression(cSplit[1], inputs, subtasks);
               if (debugParse) {
                  System.out.println("  outputs extracted: "+outVar);
               }
               result += "   "+outVar + " = " + s + ";\n";
            } else {
               System.err.println("Parsing failed - malformed action clause "+clause);
            }
         }
      }
      if (debugParse) {
         System.out.println(" Inputs extracted: "+inputs);
         System.out.println(" Outputs extracted: "+outputs);
         System.out.println(" Return action:\n"+result);
      }
      return result;
   }
   
   /**
    * Parse a subexpression.
    * 
    * NB! Subtasks as subexpressions are not allowed for now.
    * 
    * @param exp
    * @param inputs
    * @param subtasks
    * @return
    */
   private String parseExpression(String exp, HashSet<String> inputs, 
         List<SubtaskMethod> subtasks) {
      if (debugParse) {
         System.out.println("   Parsing expression "+exp);
      }
      ArrayList<String> ins = new ArrayList<String>();
      String[] split1 = exp.split("\\+|-|\\*|/|\\(|\\)");
      for (String s : split1) {
    	  if () {
    		  // Ignore method names
    		  continue;
    	  }
         if (s.length()>0 && !s.matches("\\d*") && !s.contains(".")) {
            ins.add(s.trim());
         }
      }
      String result = exp;
      inputs.addAll(ins);
      
      if (debugParse) {
         System.out.println("    inputs extracted: "+ins);
         System.out.println("    subtasks extracted: "+subtasks);
         System.out.println("    expression code: "+result);
      }
      return result;
   }

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