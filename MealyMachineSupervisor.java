

import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ee.ioc.cs.vsle.api.Scheme;

public class Parser {
    static String[] arithmOps = new String[] {"+","-","*","/","%"};
    static String[] comparOps = new String[] {"==","!=","<=",">=","<",">"};
    static String[] logicOps = new String[] {"&&","||","!","&","|","~","^","<<",">>"};
    static String[] evalOps = new String[] {"=","+=","-=","*=","/=","%="};
    static String[] unaryEvalOps = new String[] {"++","--"};
    static String nameRegEx = "[\\w&&\\D]\\w*";
    static String indexRegEx = "(\\[.+\\])*?";
    static String dotNameRegEx = "(("+nameRegEx+")(\\."+nameRegEx+")*)";
    static String dotIndexNameRegEx = "(("+nameRegEx+")"+indexRegEx+"(\\."+nameRegEx+indexRegEx+")*)";
    static String justNumberRegEx = "^\\-?\\d*\\.?\\d+$";
    boolean debugParse = false;
    Scheme scheme;

    public Parser(Scheme scheme, boolean debugParse) {
        this.scheme = scheme;
        this.debugParse = debugParse;
    }

    /** Statement parser
     *  Expects spaceless input!
     * @param stmt
     * @param ins
     * @param outs
     * @param stms
     * @return
     */
    public String parseStatement(String stmt, HashSet<String> ins,	HashSet<String> outs, 
            List<SubtaskMethod> stms) {
        debugParse("\n Parsing statement: "+stmt+"\n");
        String trimmed = stmt.replaceAll("\\s+", "");

        String subtaskRegex = "^\\[(.+)\\]=(\\[.+\\Q|-\\E.+\\Q->\\E.+\\])\\((.+)\\)$";
        String evaluationRegex = "^"+dotIndexNameRegEx+appendAlternatives("()",evalOps)+"(.+)$";
        String unaryRegex = "^"+dotIndexNameRegEx+appendAlternatives("()",unaryEvalOps)+"$";
        String methodCallRegex = "^"+dotNameRegEx+"\\((.*)\\)$";

        String result;
        HashSet<String> inputs = new HashSet<String>();
        HashSet<String> outputs = new HashSet<String>();

        if (trimmed.matches(subtaskRegex)) {
            debugParse(" - subtask match!");
            debugParse("    "+subtaskRegex);
            for (int i=1; i<=3; i++) {
                debugParse("     "+i+": "+trimmed.replaceAll(subtaskRegex, "$"+i));
            }
            SubtaskMethod stm = new SubtaskMethod();
            stm.prepare(scheme, trimmed.replaceAll(subtaskRegex, "$2"));
            stm.inputList = parseList(trimmed.replaceAll(subtaskRegex, "$3"), inputs, outputs, stms);
            stm.outputs = trimmed.replaceAll(subtaskRegex, "$1").split(",");
            // Extract output variable names
            for (String var : stm.outputs) {
                if (var.matches(dotIndexNameRegEx)) {
                    outputs.add(var.replaceAll(dotIndexNameRegEx, "$2"));
                } else {
                    error(" Parse error! Cannot extract output from "+stmt);
                }
            }
            stms.add(stm);
            stm.myIndex = stms.indexOf(stm);
            result = stm.getJavaScript();

        } else if (trimmed.matches(evaluationRegex)) {
            debugParse(" - evaluation match!");
            debugParse("    "+evaluationRegex);
            for (int i=1; i<=7; i++) {
                debugParse("     "+i+": "+trimmed.replaceAll(evaluationRegex, "$"+i));
            }
            outputs.add(trimmed.replaceAll(evaluationRegex, "$2"));
            result = "   "+parseExpression(trimmed.replaceAll(evaluationRegex, "$1"), inputs, outputs, stms);
            result += trimmed.replaceAll(evaluationRegex, "$6");
            result += parseExpression(trimmed.replaceAll(evaluationRegex, "$7"), inputs, outputs, stms);
            result += ";\n";

        } else if (stmt.trim().matches(unaryRegex)) {
            debugParse(" - unary match!");
            outputs.add(trimmed.replaceAll(unaryRegex, "$2"));
            result = "   "+trimmed+";\n";

        } else if (trimmed.matches(methodCallRegex)) {
            debugParse(" - method call match!");
            for (int i=1; i<=4; i++) {
                debugParse("     "+i+": "+trimmed.replaceAll(methodCallRegex, "$"+i));
            }
            result = "   "+trimmed.replaceAll(methodCallRegex, "$1")+"(";
            result += parseList(trimmed.replaceAll(methodCallRegex, "$4"), inputs, outputs, stms);
            result += ");\n";

        } else {
            error(" Parse error! Cannot match "+stmt);
            return stmt;

        }
        debugParse(" -> "+result);
        debugParse("    "+inputs);
        debugParse("    "+outputs);
        ins.addAll(inputs);
        outs.addAll(outputs);
        return result;
    }

    /** Expression parser
     *  Expects spaceless input!
     * @param expr
     * @param ins
     * @param outs
     * @param stms
     * @return
     */
    public String parseExpression(String expr, HashSet<String> ins, 
            HashSet<String> outs, List<SubtaskMethod> stms) {
        debugParse("   Parsing expression: "+expr);
        if (expr.isEmpty()) {  // Nothing to to with empty
            debugParse("    - empty");
            return "";
        } else if (expr.matches("\\d+")) {  // Nothing to do with numerics
            debugParse("    - just numeric");
            return expr;
        } else if (expr.startsWith("(") && expr.endsWith(")")) {  // Bracketed expression
            debugParse("    - bracketed expression");
            String result = "("+parseExpression(expr.substring(1, expr.length()-1), ins, outs, stms)+")";
            return result;
        }

        // If there are operators not enclosed in brackets, split the expression 
        // from there and parse both sides separately (recursively)
        String opsRegEx = appendAlternatives("()", arithmOps);
        opsRegEx = appendAlternatives(opsRegEx, logicOps);
        opsRegEx = appendAlternatives(opsRegEx, comparOps);
        Pattern p = Pattern.compile(opsRegEx);
        Matcher m = p.matcher(expr);
        while (m.find()) {
            int endIndex = m.start();
            // Compare the number of opening and closing brackets up to the endIndex
            if (count(expr, endIndex, "(")==count(expr, endIndex, ")") 
                    && count(expr, endIndex, "[")==count(expr, endIndex, "]")) {
                debugParse("    - basic operation");
                String head = parseExpression(expr.substring(0, endIndex), ins, outs, stms);
                String operator = m.group();
                String tail = parseExpression(expr.substring(endIndex+operator.length()), ins, outs, stms);
                String result = head+operator+tail;
                return result;
            }
        }

        // Method call
        String methodCallRegex = "^"+dotNameRegEx+"\\((.*)\\)$";
        if (expr.matches(methodCallRegex)) {
            debugParse("    - method call");
            String result = expr.replaceAll(methodCallRegex, "$1");
            result += "("+parseList(expr.replaceAll(methodCallRegex, "$4"), ins, outs, stms)+")";
            return result;
        }

        // Subtask
        String subtaskCallRegex = "^(\\[.+\\Q|-\\E.+\\Q->\\E.+\\])\\((.+)\\)$";
        if (expr.matches(subtaskCallRegex)) {
            debugParse("    - subtask");
            SubtaskMethod stm = new SubtaskMethod();
            stm.prepare(scheme, expr.replaceAll(subtaskCallRegex, "$1"));
            stm.inputList = parseList(expr.replaceAll(subtaskCallRegex, "$2"), ins, outs, stms);
            stms.add(stm);
            stm.myIndex = stms.indexOf(stm);
            return stm.getJavaScript();
        }

        if (expr.matches("^"+dotIndexNameRegEx+"$")) {  // A variable
            debugParse("    - just variable");
            String result = expr.replaceAll("^"+dotIndexNameRegEx+"$", "$2");
            ins.add(result);
            result += parseIndexes(expr.replaceAll("^"+dotIndexNameRegEx+"$", "$3$4$5"), ins, outs, stms);
            return result;
        }

        error("Parse error! Unable to match: "+expr);
        return "";
    }

    String parseList(String list, HashSet<String> ins, HashSet<String> outs, 
            List<SubtaskMethod> stms) {
        debugParse("   Parsing list: "+list);
        Pattern p = Pattern.compile(",");
        Matcher m = p.matcher(list);
        while (m.find()) {
            int endIndex = m.start();
            // Compare the number of opening and closing brackets up to the endIndex
            if (count(list, endIndex, "(")==count(list, endIndex, ")") 
                    && count(list, endIndex, "[")==count(list, endIndex, "]")) {
                String head = parseExpression(list.substring(0, endIndex), ins, outs, stms);
                String operator = m.group();
                String tail = parseList(list.substring(endIndex+operator.length()), ins, outs, stms);
                String result = head+operator+tail;
                return result;
            }
        }
        // No commas in list
        return parseExpression(list, ins, outs, stms);
    }

    String parseIndexes(String expr, HashSet<String> ins, HashSet<String> outs, 
            List<SubtaskMethod> stms) {
        debugParse("   Parsing indexes: "+expr);
        int startIdx = expr.indexOf("[");
        if (startIdx == -1) {  // No more indexes
            return expr;
        }
        int endIdx = startIdx+1;
        // Find the pairing square bracket
        while ((endIdx = expr.indexOf("]", endIdx)+1) > 0) {
            // Compare the number of opening and closing brackets up to the endIndex
            if (count(expr, endIdx, "[")==count(expr, endIdx, "]")) {
                String head = expr.substring(0, startIdx);
                head += "["+parseExpression(expr.substring(startIdx+1, endIdx-1), ins, outs, stms)+"]";
                String tail = parseIndexes(expr.substring(endIdx), ins, outs, stms);
                return head+tail;
            }
        }
        error("Parse error! Unable to find pairs of square brackets "+ expr);
        return expr;
    }

    int count(String str, int endIdx, String sub) {
        if (str.isEmpty() || sub.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ( ((idx = str.indexOf(sub, idx)) != -1) && idx<endIdx ) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    String appendAlternatives(String regex, String[] values) {
        regex = regex.substring(0, regex.length()-1);
        for (String op : values) {
            regex += "\\Q"+op+"\\E"+"|";
        }
        regex = regex.substring(0, regex.length()-1) + ")";
        return regex;
    }

    void debugParse(String str) {
        if (debugParse) {
            System.out.println(str);
        }
    }

    void error(String str) {
        System.out.println(str);
    }

}
