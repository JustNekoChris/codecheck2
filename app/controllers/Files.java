package controllers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.script.ScriptException;

import com.horstmann.codecheck.Problem;
import com.horstmann.codecheck.Util;

import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;


// No Longer Used
// import java.net.URL;
// import java.text.MessageFormat;
// import java.util.List;
// import com.fasterxml.jackson.databind.node.ObjectNode;
// import com.typesafe.config.Config;
// import models.CodeCheck;
// import play.Logger;
// New Classes Added
// import java.lang.System.Logger;

public class Files extends Controller {     
    @Inject private FilesService filesService;
    
    public Result filesHTML2(Http.Request request, String repo, String problemName, String ccid)
            throws IOException, NoSuchMethodException, ScriptException {
        if (ccid == null) {
            Optional<Http.Cookie> ccidCookie = request.getCookie("ccid");
            ccid = ccidCookie.map(Http.Cookie::value).orElse(Util.createPronouncableUID());
        }
        String result = filesService.filesHTML2_2(models.Util.prefix(request), repo, problemName, ccid);
        if (result.equals("0")) return badRequest("Cannot load problem " + repo + "/" + problemName);
        filesService.wakeupChecker();
        Http.Cookie newCookie = models.Util.buildCookie("ccid", ccid);
        return ok(result).withCookies(newCookie).as("text/html");
    }
    
    public Result tracer(Http.Request request, String repo, String problemName, String ccid)
            throws IOException {
        if (ccid == null) {
            Optional<Http.Cookie> ccidCookie = request.getCookie("ccid");
            ccid = ccidCookie.map(Http.Cookie::value).orElse(Util.createPronouncableUID());
        }
        String result = filesService.tracer2(ccid, filesService.getProblemFiles(problemName, repo, ccid));
        if (result.equals("0")) return badRequest("Cannot load problem " + repo + "/" + problemName);
        Http.Cookie newCookie = models.Util.buildCookie("ccid", ccid);
        return ok(result).withCookies(newCookie).as("text/html");
    }
        
    // TODO: Caution--this won't do the right thing with param.js randomness when
    // used to prebuild UI like in ebook, Udacity
    public Result fileData(Http.Request request, String repo, String problemName, String ccid)
            throws IOException, NoSuchMethodException, ScriptException {
        if (ccid == null) {
            Optional<Http.Cookie> ccidCookie = request.getCookie("ccid");
            ccid = ccidCookie.map(Http.Cookie::value).orElse(Util.createPronouncableUID());
        }
        Map<Path, byte[]> problemFiles = filesService.getProblemFiles(problemName, repo, ccid);
        if (problemFiles.isEmpty()) return badRequest("Cannot load problem " + repo + "/" + problemName);      
        Problem problem = new Problem(problemFiles);
        Http.Cookie newCookie = models.Util.buildCookie("ccid", ccid);
        return ok(models.Util.toJson(problem.getProblemData())).withCookies(newCookie);
    }

    // TODO: Legacy, also codecheck.js

    public Result filesHTML(Http.Request request, String repo, String problemName, String ccid)
            throws IOException, NoSuchMethodException, ScriptException {
        if (ccid == null) {
            Optional<Http.Cookie> ccidCookie = request.getCookie("ccid");
            ccid = ccidCookie.map(Http.Cookie::value).orElse(Util.createPronouncableUID());
        }
        String result = filesService.filesHTML_2(repo, problemName, ccid); 
        if (result.equals("0")) return badRequest("Cannot load problem " + repo + "/" + problemName);
        Http.Cookie newCookie = models.Util.buildCookie("ccid", ccid);
        return ok(result.toString()).withCookies(newCookie).as("text/html");
    }       
}
