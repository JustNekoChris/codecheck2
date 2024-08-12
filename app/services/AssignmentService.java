package services;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AssignmentService {
    
    
    public static ArrayNode parseAssignment(String assignment) {
        if (assignment == null || assignment.trim().isEmpty()) 
            throw new IllegalArgumentException("No assignments");
        ArrayNode groupsNode = JsonNodeFactory.instance.arrayNode();
        Pattern problemPattern = Pattern.compile("\\s*(\\S+)(\\s+[0-9.]+%)?(.*)");
        String[] groups = assignment.split("\\s+-{3,}\\s+");
        for (int problemGroup = 0; problemGroup < groups.length; problemGroup++) {
            String[] lines = groups[problemGroup].split("\\n+");
            if (lines.length == 0) throw new IllegalArgumentException("No problems given");
            ArrayNode group = JsonNodeFactory.instance.arrayNode();
            for (int i = 0; i < lines.length; i++) {
                ObjectNode problem = JsonNodeFactory.instance.objectNode();
                Matcher matcher = problemPattern.matcher(lines[i]);
                if (!matcher.matches())
                    throw new IllegalArgumentException("Bad input " + lines[i]);
                String problemDescriptor = matcher.group(1); // URL or qid
                String problemURL;
                String qid = null;
                boolean checked = false;
                if (problemDescriptor.startsWith("https")) problemURL = problemDescriptor;
                else if (problemDescriptor.startsWith("http")) {
                    if (!problemDescriptor.startsWith("http://localhost") && !problemDescriptor.startsWith("http://127.0.0.1")) {
                        problemURL = "https" + problemDescriptor.substring(4);
                    }
                    else
                        problemURL = problemDescriptor;                    
                }   
                else if (problemDescriptor.matches("[a-zA-Z0-9_]+(-[a-zA-Z0-9_]+)*")) { 
                    qid = problemDescriptor;
                    problemURL = "https://www.interactivities.ws/" + problemDescriptor + ".xhtml";
                    if (com.horstmann.codecheck.Util.exists(problemURL))
                        checked = true;
                    else
                        problemURL = "https://codecheck.it/files?repo=wiley&problem=" + problemDescriptor;                                                          
                }
                else throw new IllegalArgumentException("Bad problem: " + problemDescriptor);
                if (!checked && !com.horstmann.codecheck.Util.exists(problemURL))
                    throw new IllegalArgumentException("Cannot find " + problemDescriptor);             
                problem.put("URL", problemURL);
                if (qid != null) problem.put("qid", qid);
                
                String weight = matcher.group(2);
                if (weight == null) weight = "100";
                else weight = weight.trim().replace("%", "");
                problem.put("weight", Double.parseDouble(weight) / 100);

                String title = matcher.group(3);
                if (title != null) { 
                    title = title.trim();
                    if (!title.isEmpty())
                        problem.put("title", title);
                }
                group.add(problem);
            }
            groupsNode.add(group);
        }
        return groupsNode;
    }
    
    private static boolean isProblemKeyFor(String key, ObjectNode problem) {        
        // Textbook repo
        if (problem.has("qid")) return problem.get("qid").asText().equals(key);
        String problemURL = problem.get("URL").asText();
        // Some legacy CodeCheck questions have butchered keys such as 0101407088y6iesgt3rs6k7h0w45haxajn 
        return problemURL.endsWith(key);
    }
             
    public static double score(ObjectNode assignment, ObjectNode work) {
        ArrayNode groups = (ArrayNode) assignment.get("problems");      
        String workID = work.get("workID").asText();
        ArrayNode problems = (ArrayNode) groups.get(workID.hashCode() % groups.size());
        ObjectNode submissions = (ObjectNode) work.get("problems");
        double result = 0;
        double sum = 0;
        for (JsonNode p : problems) {
            ObjectNode problem = (ObjectNode) p;
            double weight = problem.get("weight").asDouble();
            sum += weight;
            for (String key : com.horstmann.codecheck.Util.iterable(submissions.fieldNames())) {
                if (isProblemKeyFor(key, problem)) {    
                    ObjectNode submission = (ObjectNode) submissions.get(key);
                    result += weight * submission.get("score").asDouble();
                }
            }           
        }
        return sum == 0 ? 0 : result / sum;
    }
    
    public static boolean editKeyValid(String suppliedEditKey, ObjectNode assignmentNode) {
        String storedEditKey = assignmentNode.get("editKey").asText();
        return suppliedEditKey.equals(storedEditKey) && !suppliedEditKey.contains("/");
          // Otherwise it's an LTI edit key (tool consumer ID + user ID)
    }
}
