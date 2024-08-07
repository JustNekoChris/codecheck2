package controllers;

import java.io.IOException;
// import java.io.InputStream;
// import java.lang.System.Logger;
// import java.net.URL;
// import java.nio.charset.StandardCharsets;
// import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
// import java.time.Instant;
import java.util.Map;
import java.util.Optional;
// import java.util.regex.Matcher;
// import java.util.regex.Pattern;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
// import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
// import com.horstmann.codecheck.Problem;
// import com.horstmann.codecheck.Util;

import models.AssignmentConnector;
// import models.CodeCheck;
import models.LTI;
// import play.Logger;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import service.LTIProblemService;

public class LTIProblem extends Controller {
    @Inject private AssignmentConnector assignmentConn;
    @Inject private LTI lti;
    // @Inject private CodeCheck codeCheck;
    @Inject private LTIProblemService ltiProblemService;
    
    private static System.Logger logger = System.getLogger("com.horstmann.codecheck");
    
    private ObjectNode ltiNode(Http.Request request) {
        Map<String, String[]> postParams = request.body().asFormUrlEncoded();
        if (!lti.validate(request)) throw new IllegalArgumentException("Failed OAuth validation");
        
        return ltiProblemService.ltiNodeMake(postParams);
    }

    public Result launch(Http.Request request) throws IOException {    
        try {
            ObjectNode ltiNode = ltiNode(request);
            
            String qid = request.queryString("qid").orElse(null);
            // TODO: What about CodeCheck qids?
            if (qid == null) return badRequest("No qid");
            return ok(ltiProblemService.launchDocument(ltiNode, qid)).as("text/html");
        } catch (Exception ex) {
            logger.log(System.Logger.Level.ERROR, "launch: Cannot load problem " + request, ex);
            return badRequest("Cannot load problem: " + ex.getMessage());
        }
    }       
    
    public Result launchCodeCheck(Http.Request request, String repo, String problemName) {
        try {           
            // TODO: Now the client will do the LTI communication. CodeCheck should do it.
            ObjectNode ltiNode = ltiNode(request);
            Optional<Http.Cookie> ccidCookie = request.getCookie("ccid");
            String ccid = ccidCookie.map(Http.Cookie::value).orElse(com.horstmann.codecheck.Util.createPronouncableUID());

            String document = ltiProblemService.launchCodeCheckDocument(ltiNode, repo, problemName, ccid);
            
            Http.Cookie newCookie = controllers.Util.buildCookie("ccid", ccid);         
            return ok(document).withCookies(newCookie).as("text/html");
        }  catch (Exception ex) {
            logger.log(System.Logger.Level.ERROR, "launchCodeCheck: Cannot load problem " + repo + "/" + problemName, ex);
            return badRequest("Cannot load problem " + repo + "/" + problemName);
        }
    }
    
      
    
    public Result launchTracer(Http.Request request, String repo, String problemName) {
        try {           
            ObjectNode ltiNode = ltiNode(request);
            Optional<Http.Cookie> ccidCookie = request.getCookie("ccid");
            String ccid = ccidCookie.map(Http.Cookie::value).orElse(com.horstmann.codecheck.Util.createPronouncableUID());

            String result = ltiProblemService.launchTracerResult(ltiNode, repo, problemName, ccid);

            Http.Cookie newCookie = controllers.Util.buildCookie("ccid", ccid);         
            return ok(result).withCookies(newCookie).as("text/html");
        }  catch (Exception ex) {
            logger.log(System.Logger.Level.ERROR, "launchTracer: Cannot load problem " + repo + "/" + problemName, ex);
            return badRequest("Cannot load problem " + repo + "/" + problemName);
        }    
    }
    
    public Result send(Http.Request request) throws IOException, NoSuchAlgorithmException {
        ObjectNode requestNode = (ObjectNode) request.body().asJson();
        String submissionID = requestNode.get("submissionID").asText();
        try {
            return ok(ltiProblemService.sendResultNode(requestNode, submissionID));
        } catch (Exception e) {
            logger.log(System.Logger.Level.ERROR, "send: Cannot send submission " + submissionID + " " + e.getMessage());
            return badRequest(e.getMessage());
        }
    }
    
    public Result retrieve(Http.Request request) throws IOException {
        ObjectNode requestNode = (ObjectNode) request.body().asJson();
        String submissionID = requestNode.get("submissionID").asText();
        try {
            ObjectNode result = assignmentConn.readNewestJsonObjectFromDB("CodeCheckSubmissions", "submissionID", submissionID);          
            ObjectMapper mapper = new ObjectMapper();
            result.set("state", mapper.readTree(result.get("state").asText()));
            return ok(result);
        } catch (Exception e) {
            logger.log(System.Logger.Level.ERROR, "retrieve: Cannot retrieve submission " + submissionID + " " + e.getClass() + " " + e.getMessage());
            return badRequest("retrieve: Cannot retrieve submission " + submissionID);
        }
    }   
}