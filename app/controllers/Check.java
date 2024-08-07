package controllers;

import java.io.IOException;
// import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
// import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
// import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horstmann.codecheck.Util;

// import models.CodeCheck;
// import play.Logger; 
import play.libs.Files.TemporaryFile;
// import play.libs.Json;
import play.libs.concurrent.HttpExecution;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import service.CheckService;

public class Check extends Controller {
    private CodecheckExecutionContext ccec; 
    @Inject private CheckService checkService;
    
    // TODO: Legacy HTML report, used in Core Java for the Impatient 2e, 3e
    public CompletableFuture<Result> checkHTML(Http.Request request) throws IOException, InterruptedException {
        Map<String, String[]> params = request.body().asFormUrlEncoded();
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<Http.Cookie> ccidCookie = request.getCookie("ccid");
                String ccid = ccidCookie.map(Http.Cookie::value).orElse(com.horstmann.codecheck.Util.createPronouncableUID());
                String[] result = checkService.checkHTMLreport(ccid, params);
                Http.Cookie newCookie = models.Util.buildCookie("ccid", result[0]);
                return ok(result[1]).withCookies(newCookie).as("text/html");
            }
            catch (Exception ex) {
                return internalServerError(Util.getStackTrace(ex));
            }
        }, HttpExecution.fromThread((Executor) ccec) /* ec.current() */);                        
    }

    // Core Java, run with input
    public CompletableFuture<Result> run(Http.Request request) throws IOException, InterruptedException  {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String[]> params;
	            Map<Path, String> submissionFiles = new TreeMap<>();
	            String contentType = request.contentType().orElse("");
		        if ("application/x-www-form-urlencoded".equals(contentType)) {
                    params = request.body().asFormUrlEncoded();
                    return ok(checkService.run_x_www_form_urlencoded(params, submissionFiles)).as("text/plain");
		        } else if ("multipart/form-data".equals(contentType)) {
		            play.mvc.Http.MultipartFormData<TemporaryFile> body = request.body().asMultipartFormData();
		            for (var f : body.getFiles()) {
	                    System.getLogger("com.horstmann.codecheck.lti").log(System.Logger.Level.INFO, "f=" + f.getKey() + " " + f.getFilename());
		            	String name = f.getFilename();
		                TemporaryFile tempZipFile = f.getRef();
		                Path savedPath = tempZipFile.path();
		                String contents = Util.read(savedPath);
                        submissionFiles.put(Paths.get(name), contents);
	                }
	                return ok(checkService.run_form_data(submissionFiles)).as("text/plain");
		        } else if ("application/json".equals(contentType)) {
			        return ok(checkService.run_json(request.body().asJson(), submissionFiles)).as("application/json");
		        }
		        else return internalServerError("Bad content type");
            } catch (Exception ex) {
            	return internalServerError(Util.getStackTrace(ex));
            }	
        }, HttpExecution.fromThread((Executor) ccec) /* ec.current() */); 
    }
            
    // From JS UI
    public CompletableFuture<Result> checkNJS(Http.Request request) throws IOException, InterruptedException  {
        Map<String, String[]> params;
        if ("application/x-www-form-urlencoded".equals(request.contentType().orElse(""))) 
            params = request.body().asFormUrlEncoded();
        else if ("application/json".equals(request.contentType().orElse(""))) {
            params = new HashMap<>();
            JsonNode json = request.body().asJson();
            Iterator<Entry<String, JsonNode>> iter = json.fields();
            while (iter.hasNext()) {
                Entry<String, JsonNode> entry = iter.next();
                params.put(entry.getKey(), new String[] { entry.getValue().asText() });         
            };
        }
        else 
            params = request.queryString();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<Http.Cookie> ccidCookie = request.getCookie("ccid");
                String ccid = ccidCookie.map(Http.Cookie::value).orElse(com.horstmann.codecheck.Util.createPronouncableUID());
                ObjectNode result = checkService.checkNJSResult(ccid, params);
                Http.Cookie newCookie = models.Util.buildCookie("ccid", ccid);
                return ok(result).withCookies(newCookie).as("application/json");
            } catch (Exception ex) {
                return internalServerError(Util.getStackTrace(ex));
            }
        }, HttpExecution.fromThread((Executor) ccec) /* ec.current() */);           
    }
}
