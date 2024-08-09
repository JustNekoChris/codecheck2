package controllers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horstmann.codecheck.Util;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.FilesService;

import javax.inject.Inject;
import javax.script.ScriptException;
import java.io.IOException;
import java.util.Optional;

public class Files extends Controller {     
    @Inject private FilesService filesService;
    
    public Result filesHTML2(Http.Request request, String repo, String problemName, String ccid)
            throws IOException, NoSuchMethodException, ScriptException {
        if (ccid == null) {
            Optional<Http.Cookie> ccidCookie = request.getCookie("ccid");
            ccid = ccidCookie.map(Http.Cookie::value).orElse(Util.createPronouncableUID());
        }
        String result = filesService.filesHTML2(controllers.Util.prefix(request), repo, problemName, ccid);
        if (result == null) return badRequest("Cannot load problem " + repo + "/" + problemName);
        Http.Cookie newCookie = controllers.Util.buildCookie("ccid", ccid);
        return ok(result).withCookies(newCookie).as("text/html");
    }
    
    public Result tracer(Http.Request request, String repo, String problemName, String ccid)
            throws IOException {
        if (ccid == null) {
            Optional<Http.Cookie> ccidCookie = request.getCookie("ccid");
            ccid = ccidCookie.map(Http.Cookie::value).orElse(Util.createPronouncableUID());
        }
        String result = filesService.tracer(repo, problemName, ccid);
        if (result == null) return badRequest("Cannot load problem " + repo + "/" + problemName);
        Http.Cookie newCookie = controllers.Util.buildCookie("ccid", ccid);
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
        ObjectNode result = filesService.fileData(repo, problemName, ccid);
        if (result == null) return badRequest("Cannot load problem " + repo + "/" + problemName);
        Http.Cookie newCookie = controllers.Util.buildCookie("ccid", ccid);
        return ok(result).withCookies(newCookie);
    }

    // TODO: Legacy, also codecheck.js

    public Result filesHTML(Http.Request request, String repo, String problemName, String ccid)
            throws IOException, NoSuchMethodException, ScriptException {
        if (ccid == null) {
            Optional<Http.Cookie> ccidCookie = request.getCookie("ccid");
            ccid = ccidCookie.map(Http.Cookie::value).orElse(Util.createPronouncableUID());
        }
        String result = filesService.filesHTML(repo, problemName, ccid);
        if (result == null) return badRequest("Cannot load problem " + repo + "/" + problemName);
        Http.Cookie newCookie = controllers.Util.buildCookie("ccid", ccid);
        return ok(result.toString()).withCookies(newCookie).as("text/html");
    }       
}