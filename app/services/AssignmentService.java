package services;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horstmann.codecheck.Util;

import models.AssignmentConnector;

public class AssignmentService {
    @Inject private AssignmentConnector assignmentConn;
    
    
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

    public ArrayNode makeSubmissions(String assignmentID, String editKey) 
            throws IOException {
        ObjectNode assignmentNode = assignmentConn.readJsonObjectFromDB("CodeCheckAssignments", "assignmentID", assignmentID);
        // if (assignmentNode == null) return badRequest("Assignment not found");
        if (assignmentNode == null) return null;
        
        if (!editKeyValid(editKey, assignmentNode))
            throw new IllegalArgumentException("Edit key does not match");

        ArrayNode submissions = JsonNodeFactory.instance.arrayNode();

        Map<String, ObjectNode> itemMap = assignmentConn.readJsonObjectsFromDB("CodeCheckWork", "assignmentID", assignmentID, "workID");

        for (String submissionKey : itemMap.keySet()) {
            String[] parts = submissionKey.split("/");
            String ccid = parts[0];
            String submissionEditKey = parts[1];
            
            ObjectNode work = itemMap.get(submissionKey);
            ObjectNode submissionData = JsonNodeFactory.instance.objectNode();
            submissionData.put("opaqueID", ccid);
            submissionData.put("score", score(assignmentNode, work));
            submissionData.set("submittedAt", work.get("submittedAt"));
            submissionData.put("viewURL", "/private/submission/" + assignmentID + "/" + ccid + "/" + submissionEditKey); 
            submissions.add(submissionData);            
        }
        return submissions;
    }

    public Map<String, Object> workInfo(ObjectNode assignmentNode, boolean isStudent, String ccid, String editKey, String assignmentID,
    boolean editKeySaved, boolean ccidCookieExists, String ccidCookieValue, boolean editKeyCookieExists, String editKeyCookieValue, String workID) 
            throws IOException {
        assignmentNode.put("isStudent", isStudent);
        if (isStudent) {
            if (ccid == null) {         
                if (ccidCookieExists) {
                    ccid = ccidCookieValue;
                    if (editKeyCookieExists) 
                        editKey = editKeyCookieValue;
                    else { // This shouldn't happen, but if it does, clear ID
                        ccid = com.horstmann.codecheck.Util.createPronouncableUID();
                        editKey = Util.createPrivateUID();
                        editKeySaved = false;                       
                    }
                } else { // First time on this browser
                    ccid = com.horstmann.codecheck.Util.createPronouncableUID();
                    editKey = Util.createPrivateUID();
                    editKeySaved = false;
                }
            } else if (editKey == null) { // Clear ID request
                ccid = com.horstmann.codecheck.Util.createPronouncableUID();
                editKey = Util.createPrivateUID();
                editKeySaved = false;               
            }
            assignmentNode.put("clearIDURL", "/assignment/" + assignmentID + "/" + ccid);
            workID = ccid + "/" + editKey;          
        } else { // Instructor
            if (ccid == null && editKey != null && !AssignmentService.editKeyValid(editKey, assignmentNode))
                throw new IllegalArgumentException("Edit key does not match");
            if (ccid != null && editKey != null) {  // Instructor viewing student submission
                assignmentNode.put("saveCommentURL", "/saveComment"); 
                workID = ccid + "/" + editKey;
                // Only put workID into assignmentNode when viewing submission as Instructor, for security reason
                assignmentNode.put("workID", workID);
            }
        }
        assignmentNode.remove("editKey");
        ArrayNode groups = (ArrayNode) assignmentNode.get("problems");
        assignmentNode.set("problems", groups.get(Math.abs(workID.hashCode()) % groups.size()));
        
        // Start reading work and comments
        String work = null;
        ObjectNode commentObject = null;
        String comment = null;
        if (!workID.equals(""))  {
            work = assignmentConn.readJsonStringFromDB("CodeCheckWork", "assignmentID", assignmentID, "workID", workID);
            commentObject = assignmentConn.readJsonObjectFromDB("CodeCheckComments", "assignmentID", assignmentID, "workID", workID);
        }
        if (work == null) 
            work = "{ assignmentID: \"" + assignmentID + "\", workID: \"" 
                + workID + "\", problems: {} }";
        if (commentObject == null)
            comment = "";
        else
            comment = commentObject.get("comment").asText();
        assignmentNode.put("comment", comment);

        return Map.of("assignmentNode", assignmentNode, "work", work, "workID", workID, "editKey", editKey, 
            "ccid", ccid, "editKeySaved", editKeySaved);
    }
}
