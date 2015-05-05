
import java.util.Arrays;

import ee.ioc.cs.vsle.api.Scheme;

public class SubtaskMethod {
    Scheme scheme;
    public String subtaskModel;
    public String[] subtaskInputs;
    public String[] subtaskOutputs;
    public String inputList;
    public String[] outputs;
    public int myIndex;

    /**
     * Dynamic approach for convenience.
     * 
     * The independent subtask should be specified as [Model|-inputs->outputs]
     * Spacing should be removed before!
     * 
     * @param independentSubtask
     * @param scheme
     * @return store to internal variables
     */
    public void prepare(Scheme scheme, String independentSubtask) {
        this.scheme = scheme;
        String istRegEx = "\\[(.+)\\Q|-\\E(.+)\\Q->\\E(.+)\\]";
        subtaskModel = independentSubtask.replaceAll(istRegEx,"$1");
        subtaskInputs = independentSubtask.replaceAll(istRegEx,"$2").split(",");
        subtaskOutputs = independentSubtask.replaceAll(istRegEx,"$3").split(",");
    }

    /**
     * Preserved variablenames in JavaScript: stms, stOutput
     * stms is the list of SubtaskMethods
     * @return
     */
    public String getJavaScript() {
        String body = "stms["+myIndex+"].execute(["+inputList+"])";
        if (outputs==null || outputs.length <=1) {  // Subtask expression
            return body+"[0]";
        } else {  // Subtask statement
            String result = "   stOutput="+body+";\n"; 
            for (int i=0; i<outputs.length; i++) {
                result += "   "+outputs[i]+"=stOutput["+i+"];\n";
            }
            return result;
        }
    }

    public Object[] execute(Object[] inputs) {
        Object[] result = scheme.computeModel(
                subtaskModel, subtaskInputs, subtaskOutputs, inputs, true);
        return result;
    }

    public String toString() {
        String result = "ST idx="+myIndex+" : ";
        if (outputs!=null) {
            result += Arrays.toString(outputs) + " = ";
        }
        result += "["+subtaskModel+"|-";
        result += Arrays.toString(subtaskInputs)+"->";
        result += Arrays.toString(subtaskOutputs)+"]";
        result += "(["+inputList+"])";
        return result;
    }
}
