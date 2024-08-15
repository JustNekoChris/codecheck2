package controllers;

/*
 An assignment is made up of problems. A problem is provided in a URL 
 that is displayed in an iframe. (In the future, maybe friendly problems 
 could coexist on a page or shared iframe for efficiency.) An assignment 
 weighs its problems.
 
 The "problem key" is normally the problem URL. However, for interactive or CodeCheck 
 problems in the textbook repo, it is the qid of the single question in the problem.
 
 CodeCheckWork is a map from problem keys to scores and states. It only stores the most recent version.
 CodeCheckSubmissions is an append-only log of all submissions of a single problem. 
    
 Tables:
   
 CodeCheckAssignment
   assignmentID [primary key]
   deadline (an ISO 8601 string like "2020-12-01T23:59:59Z")
   editKey // LTI: tool consumer ID + user ID
   problems
     array of // One per group
       array of { URL, qid?, weight } // qid for book repo
  
 CodeCheckLTIResources (Legacy)
   resourceID [primary key] // LTI tool consumer ID + course ID + resource ID 
   assignmentID
   
 CodeCheckWork
   assignmentID [partition key] // non-LTI: courseID? + assignmentID, LTI: toolConsumerID/courseID + assignment ID, Legacy tool consumer ID/course ID/resource ID  
   workID [sort key] // non-LTI: ccid/editKey, LTI: userID
   problems 
     map from URL/qids to { state, score }
   submittedAt
   tab     
       
 CodeCheckSubmissions
   submissionID [partition key] // non-LTI: courseID? + assignmentID + problemKey + ccid/editKey , LTI: toolConsumerID/courseID + assignmentID + problemID + userID 
     // either way, that's resource ID + workID + problem key
   submittedAt [sort key] 
   state: as string, not JSON
   score
  
   with global secondary index (TODO: Not currently)
     problemID 
     submitterID
   
 CodeCheckLTICredentials
   oauth_consumer_key [primary key]
   shared_secret

CodeCheckComments
   assignmentID [partition key] // non-LTI: courseID? + assignmentID, LTI: toolConsumerID/courseID + assignment ID, Legacy tool consumer ID/course ID/resource ID  
   workID [sort key] // non-LTI: ccid/editKey, LTI: userID
   comment

   (This is a separate table from CodeCheckWork because we can't guarantee atomic updates if a student happens to update their work while the instructor updates a comment)

 Assignment parsing format:
 
   Groups separated by 3 or more -
   Each line:
     urlOrQid (weight%)? title
 
 Cookies 
   ccid (student only)
   cckey (student only)
   PLAY_SESSION
*/


import java.io.IOException;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horstmann.codecheck.Util;

import models.AssignmentConnector;
// import play.Logger;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.AssignmentService;

public class Assignment extends Controller {
    @Inject private AssignmentConnector assignmentConn;
    @Inject private AssignmentService assignmentService;
    private static Logger logger = System.getLogger("com.horstmann.codecheck");
    
    /*
     * assignmentID == null: new assignment
     * assignmentID != null, editKey != null: edit assignment
     * assignmentID != null, editKey == null: clone assignment
     */
    public Result edit(Http.Request request, String assignmentID, String editKey) throws IOException {
        ObjectNode assignmentNode;
        if (assignmentID == null) {
            assignmentNode = JsonNodeFactory.instance.objectNode();
        } else {
            assignmentNode = assignmentConn.readJsonObjectFromDB("CodeCheckAssignments", "assignmentID", assignmentID);
            if (assignmentNode == null) return badRequest("Assignment not found");
            
            if (editKey == null) { // Clone
                assignmentNode.remove("editKey");
                assignmentNode.remove("assignmentID");
            }
            else { // Edit existing assignment
                if (!AssignmentService.editKeyValid(editKey, assignmentNode)) 
                    // In the latter case, it is an LTI toolConsumerID + userID             
                    return badRequest("editKey " + editKey + " does not match");
            }
        } 
        assignmentNode.put("saveURL", "/saveAssignment");
        return ok(views.html.editAssignment.render(assignmentNode.toString(), true));               
    }
    
    /*
     * ccid == null, editKey == null, isStudent = true:  Student starts editing
     * ccid != null, editKey == null, isStudent = true:  Student wants to change ID (hacky)
     * ccid != null, editKey != null, isStudent = true:  Student resumes editing
     * ccid != null, editKey != null, isStudent = false: Instructor views a student submission (with the student's editKey)
     * ccid == null, editKey == null, isStudent = false: Instructor views someone else's assignment (for cloning) 
     * ccid == null, editKey != null, isStudent = false: Instructor views own assignment (for editing, viewing submissions)
     */
    public Result work(Http.Request request, String assignmentID, String ccid, String editKey, boolean isStudent) 
            throws IOException, GeneralSecurityException {
        String prefix = controllers.Util.prefix(request);
        String workID = "";
        boolean editKeySaved = true;

        ObjectNode assignmentNode = assignmentConn.readJsonObjectFromDB("CodeCheckAssignments", "assignmentID", assignmentID);        
        if (assignmentNode == null) return badRequest("Assignment not found");

        Optional<Http.Cookie> ccidCookie = request.getCookie("ccid");
        boolean ccidCookieExists = ccidCookie.isPresent();
        String ccidCookieValue = ccidCookieExists ? ccidCookie.get().value() : null;

        Optional<Http.Cookie> editKeyCookie = request.getCookie("cckey");
        boolean editKeyCookieExists = editKeyCookie.isPresent();
        String editKeyCookieValue = editKeyCookieExists ? editKeyCookie.get().value() : null;

        try {
            Map<String, Object> info = assignmentService.workInfo(
                assignmentNode, isStudent, ccid, editKey, assignmentID, editKeySaved, 
                ccidCookieExists, ccidCookieValue, editKeyCookieExists, editKeyCookieValue, workID
                );
            assignmentNode = (ObjectNode) info.get("assignmentNode");
            ccid = (String) info.get("ccid");
            editKey = (String) info.get("editKey");
            workID = (String) info.get("workID");
            editKeySaved = (boolean) info.get("editKeySaved");
            String work = (String) info.get("work");
            
            String lti = "undefined";
            if (isStudent) {                        
                String returnToWorkURL = prefix + "/private/resume/" + assignmentID + "/" + ccid + "/" + editKey;
                assignmentNode.put("returnToWorkURL", returnToWorkURL); 
                assignmentNode.put("editKeySaved", editKeySaved);
                assignmentNode.put("sentAt", Instant.now().toString());
                Http.Cookie newCookie1 = controllers.Util.buildCookie("ccid", ccid);
                Http.Cookie newCookie2 = controllers.Util.buildCookie("cckey", editKey);
                return ok(views.html.workAssignment.render(assignmentNode.toString(), work, ccid, lti))
                        .withCookies(newCookie1, newCookie2);
            }
            else { // Instructor
                if (ccid == null) {
                    if (editKey != null) { // Instructor viewing for editing/submissions                    
                        // TODO: Check if there are any submissions?
                        assignmentNode.put("viewSubmissionsURL", "/private/viewSubmissions/" + assignmentID + "/" + editKey);
                        String publicURL = prefix + "/assignment/" + assignmentID;
                        String privateURL = prefix + "/private/assignment/" + assignmentID + "/" + editKey;
                        String editAssignmentURL = prefix + "/private/editAssignment/" + assignmentID + "/" + editKey;
                        assignmentNode.put("editAssignmentURL", editAssignmentURL);
                        assignmentNode.put("privateURL", privateURL);
                        assignmentNode.put("publicURL", publicURL);                 
                    }
                    String cloneURL = prefix + "/copyAssignment/" + assignmentID;
                    assignmentNode.put("cloneURL", cloneURL);
                }
                
                return ok(views.html.workAssignment.render(assignmentNode.toString(), work, ccid, lti));
            }
            
        } catch (Exception e) {
            badRequest(e.getMessage());
        }

    }
    
    public Result viewSubmissions(Http.Request request, String assignmentID, String editKey)
        throws IOException {
        ArrayNode submissions = assignmentService.makeSubmissions(assignmentID, editKey);
        if (submissions == null) return badRequest("Assignment not found");
        String allSubmissionsURL = "/lti/allSubmissions?resourceID=" + URLEncoder.encode(assignmentID, "UTF-8");
        return ok(views.html.viewSubmissions.render(allSubmissionsURL, submissions.toString()));    
    }

    /*
     * Save existing: request.assignmentID, request.editKey exist
     * New or cloned: Neither request.assignmentID nor request.editKey exist
     */
    public Result saveAssignment(Http.Request request) throws IOException {     
        ObjectNode params = (ObjectNode) request.body().asJson();

        try {
            params.set("problems", AssignmentService.parseAssignment(params.get("problems").asText()));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
        String assignmentID;
        String editKey;
        ObjectNode assignmentNode;
        if (params.has("assignmentID")) {
            assignmentID = params.get("assignmentID").asText();
            assignmentNode = assignmentConn.readJsonObjectFromDB("CodeCheckAssignments", "assignmentID", assignmentID);
            if (assignmentNode == null) return badRequest("Assignment not found");
            
            if (!params.has("editKey")) return badRequest("Missing edit key");
            editKey = params.get("editKey").asText();
            if (!AssignmentService.editKeyValid(editKey, assignmentNode)) 
                return badRequest("Edit key does not match");
        }
        else { // New assignment or clone 
            assignmentID = com.horstmann.codecheck.Util.createPublicUID();
            params.put("assignmentID", assignmentID);
            if (params.has("editKey"))
                editKey = params.get("editKey").asText();
            else { // LTI assignments have an edit key
                editKey = Util.createPrivateUID();
                params.put("editKey", editKey);
            }
            assignmentNode = null;
        }
        assignmentConn.writeJsonObjectToDB("CodeCheckAssignments", params);

        String prefix = controllers.Util.prefix(request);
        String assignmentURL = prefix + "/private/assignment/" + assignmentID + "/" + editKey;
        params.put("viewAssignmentURL", assignmentURL);
        
        return ok(params);
    }
    
    public Result saveWork(Http.Request request) throws IOException, NoSuchAlgorithmException {
        try {
            ObjectNode requestNode = (ObjectNode) request.body().asJson();
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            
            Instant now = Instant.now();
            String assignmentID = requestNode.get("assignmentID").asText();
            ObjectNode assignmentNode = assignmentConn.readJsonObjectFromDB("CodeCheckAssignments", "assignmentID", assignmentID);
            if (assignmentNode == null) return badRequest("Assignment not found");
            String workID = requestNode.get("workID").asText();
            String problemID = requestNode.get("tab").asText();
            ObjectNode problemsNode = (ObjectNode) requestNode.get("problems");
            
            String submissionID = assignmentID + " " + workID + " " + problemID; 
            ObjectNode submissionNode = JsonNodeFactory.instance.objectNode();
            submissionNode.put("submissionID", submissionID);
            submissionNode.put("submittedAt", now.toString());
            // TODO: NPE in logs for the line below
            submissionNode.put("state", problemsNode.get(problemID).get("state").toString());
            submissionNode.put("score", problemsNode.get(problemID).get("score").asDouble());
            assignmentConn.writeJsonObjectToDB("CodeCheckSubmissions", submissionNode);
            
            if (assignmentNode.has("deadline")) {
                try {
                    Instant deadline = Instant.parse(assignmentNode.get("deadline").asText());
                    if (now.isAfter(deadline)) 
                        return badRequest("After deadline of " + deadline);
                } catch (DateTimeParseException e) { // TODO: This should never happen, but it did
                    logger.log(Level.ERROR, Util.getStackTrace(e));
                }
            }
            result.put("submittedAt", now.toString());      
    
            assignmentConn.writeNewerJsonObjectToDB("CodeCheckWork", requestNode, "assignmentID", "submittedAt");
            return ok(result);
        } catch (Exception e) {
            logger.log(Level.ERROR, Util.getStackTrace(e));
            return badRequest(e.getMessage());
        }           
    }
    
    public Result saveComment(Http.Request request) throws IOException {
        try {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            ObjectNode requestNode = (ObjectNode) request.body().asJson();
            String assignmentID = requestNode.get("assignmentID").asText();
            String workID = requestNode.get("workID").asText();
            String comment = requestNode.get("comment").asText();
            
            ObjectNode commentNode = JsonNodeFactory.instance.objectNode();
            commentNode.put("assignmentID", assignmentID);
            commentNode.put("workID", workID);
            commentNode.put("comment", comment);
            assignmentConn.writeJsonObjectToDB("CodeCheckComments", commentNode);
            result.put("comment", comment);
            result.put("refreshURL", "/private/submission/" + assignmentID + "/" + workID);
            return ok(result);
        } catch (Exception e) {
            logger.log(Level.ERROR, Util.getStackTrace(e));
            return badRequest(e.getMessage());
        }           
    } 
}
