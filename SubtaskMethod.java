import java.util.Arrays;

import ee.ioc.cs.vsle.api.Scheme;

public class SubtaskMethod {
   Scheme scheme;
   public String subtaskModel;
   public String[] subtaskInputs;
   public String[] subtaskOutputs;
   public String[] inputNames;
   public String[] outputNames;
   public int myIndex;

   /**
    * Dynamic approach for convenience.
    * 
    * The independent subtask should be specified as [Model|-inputs->outputs]
    * 
    * @param independentSubtask
    * @param scheme
    * @return store to internal variables
    */
   public void prepare(Scheme scheme, String independentSubtask) {
      this.scheme = scheme;
      // System.out.println("  "+independentSubtask);
      // Remove surrounding square brackets
      independentSubtask = independentSubtask.substring(
            independentSubtask.indexOf("[")+1, independentSubtask.lastIndexOf("]")).trim();
      // Extract context
      subtaskModel = independentSubtask.substring(
            0,independentSubtask.indexOf("|-")).trim();
      // System.out.println("   model: "+subtaskModel);
      // Extract inputNames
      String inputs = independentSubtask.substring(
            independentSubtask.indexOf("|-")+2,independentSubtask.indexOf("->")).trim();
      subtaskInputs = inputs.split(",");
      for (int i = 0; i<subtaskInputs.length; i++) {
         subtaskInputs[i] = subtaskInputs[i].trim();
      }
      // System.out.println("   inputs: "+Arrays.toString(subtaskInputs));
      // Extract outputNames
      String outputs = independentSubtask.substring(
            independentSubtask.indexOf("->")+2).trim();
      subtaskOutputs = outputs.split(",");
      for (int i = 0; i<subtaskOutputs.length; i++) {
         subtaskOutputs[i] = subtaskOutputs[i].trim();
      }
      // System.out.println("   outputs: "+Arrays.toString(subtaskOutputs));
   }

  /**
    * Preserved variablenames in JavaScript: stms, stInput, stOutput
    * stms is the list of SubtaskMethods
    * @return
    */
   public String getJavaScript() {
      String result = "   stInput = "+Arrays.asList(inputNames)+ ";\n";
      result += "   stOutput = stms["+myIndex+"].execute(stInput);\n";
      for (int i=0; i<outputNames.length; i++) {
         result += "   "+outputNames[i]+"=stOutput["+i+"];\n";
      }
      return result;
   }

   public Object[] execute(Object[] inputs) {
      Object[] result = scheme.computeModel(
            subtaskModel, subtaskInputs, subtaskOutputs, inputs, true);
      return result;
   }
   
   public String toString() {
      String result = "ST idx="+myIndex+" : ";
      result += Arrays.toString(outputNames) + " = ";
      result += "["+subtaskModel+"|-";
      result += Arrays.toString(subtaskInputs)+"->";
      result += Arrays.toString(subtaskOutputs)+"]";
      result += "("+Arrays.toString(inputNames)+")";
      return result;
   }
}
