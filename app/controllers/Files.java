package controllers;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.script.ScriptException;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horstmann.codecheck.Problem;
import com.horstmann.codecheck.Util;
import com.typesafe.config.Config;

import models.CodeCheck;
// import play.Logger;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

// New Classes Added
import java.lang.System.Logger;

public class Files extends Controller {
    @Inject private CodeCheck codeCheck;    
    private static Logger logger = System.getLogger("com.horstmann.codecheck");
    @Inject private Config config;          
    @Inject private FilesService filesService;

    
public Result filesHTML2(Http.Request request, String repo, String problemName, String ccid)
            throws IOException, NoSuchMethodException, ScriptException {
        if (ccid == null) {
            Optional<Http.Cookie> ccidCookie = request.getCookie("ccid");
            ccid = ccidCookie.map(Http.Cookie::value).orElse(Util.createPronouncableUID());
        }
        String result = filesService.resultMaker(models.Util.prefix(request), repo, problemName, ccid);
        if (result.equals("0")) return badRequest("Cannot load problem " + repo + "/" + problemName);
        wakeupChecker();
        Http.Cookie newCookie = models.Util.buildCookie("ccid", ccid);
        return ok(result).withCookies(newCookie).as("text/html");
    }


    private void wakeupChecker() {
        // Wake up the checker
        String path = "com.horstmann.codecheck.comrun.remote"; 
        if (!config.hasPath(path)) return;
        String remoteURL = config.getString(path);
        if (remoteURL.isBlank()) return;
        new Thread(() -> { try {                        
            URL checkerWakeupURL = new URL(remoteURL + "/api/health");
            checkerWakeupURL.openStream().readAllBytes();
        } catch (IOException e) {
            e.printStackTrace();
        } }).start();
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
        Map<Path, byte[]> problemFiles;
        try {
            problemFiles = codeCheck.loadProblem(repo, problemName, ccid);
        } catch (Exception e) {
            logger.log(Logger.Level.ERROR, "fileData: Cannot load problem " + repo + "/" + problemName, e);
            return badRequest("Cannot load problem " + repo + "/" + problemName);
        }       
        
        Problem problem = new Problem(problemFiles);
        Http.Cookie newCookie = models.Util.buildCookie("ccid", ccid);
        return ok(models.Util.toJson(problem.getProblemData())).withCookies(newCookie);
    }

    // TODO: Legacy, also codecheck.js
    private static String start = "<!DOCTYPE html>\n<html><head>\n"
            + "<meta http-equiv='content-type' content='text/html; charset=UTF-8' />\n"
            + "<script src='/assets/download.js'></script>\n" 
            + "<script src='/assets/ace/ace.js'></script>\n"
            + "<script src='/assets/codecheck.js'></script>\n"
            + "<link type='text/css' rel='stylesheet' href='/assets/codecheck.css'/>\n" 
            + "</head><body>\n";
    private static String before = "<form method=\"post\" action=\"{0}\">\n";

    private static String fileAreaBefore = "<div id=\"{0}\" name=\"{0}\" rows=\"{1}\" cols=\"80\" class=\"editor {2}\">";
    private static String fileAreaBeforeNoEdit = "<div id=\"{0}\" name=\"{0}\" rows=\"{1}\" cols=\"{2}\" class=\"editor readonly {3}\">";
    private static String fileAreaAfter = "</div>\n";

    private static String fileOuterDiv = "<div id=\"{0}\" class=\"file\">\n";
    private static String fileOuterDivAfter = "</div>\n";

    private static String after = "<div><input id=\"submit\" type=\"submit\"/>\n"
            + "<input type=\"hidden\" name=\"repo\" value=\"{0}\"/>\n"
            + "<input type=\"hidden\" name=\"problem\" value=\"{1}\"/>\n";
    private static String formEnd = "</form>\n<div id=\"codecheck-submit-response\"></div>\n";
    private static String bodyEnd = "</body></html>";

    private static String useStart = "<p>Use the following {0,choice,1#file|2#files}:</p>\n";
    private static String provideStart = "<p>Complete the following {0,choice,1#file|2#files}:</p>\n";

    public Result filesHTML(Http.Request request, String repo, String problemName, String ccid)
            throws IOException, NoSuchMethodException, ScriptException {
        if (ccid == null) {
            Optional<Http.Cookie> ccidCookie = request.getCookie("ccid");
            ccid = ccidCookie.map(Http.Cookie::value).orElse(Util.createPronouncableUID());
        }
        Map<Path, byte[]> problemFiles;
        try {
            problemFiles = codeCheck.loadProblem(repo, problemName, ccid);
        } catch (Exception e) {
            logger.log(Logger.Level.ERROR, "filesHTML: Cannot load problem " + repo + "/" + problemName, e);
            return badRequest("Cannot load problem " + repo + "/" + problemName);
        }
        Problem problem = new Problem(problemFiles);
        Problem.DisplayData data = problem.getProblemData();
        StringBuilder result = new StringBuilder();
        result.append(start);

        if (data.description != null)
            result.append(data.description);
        String contextPath = ""; // request.host(); // TODO
        String url = contextPath + "/check";
        result.append(MessageFormat.format(before, url));
        result.append(MessageFormat.format(provideStart, data.requiredFiles.size()));

        for (Map.Entry<String, Problem.EditorState> entry : data.requiredFiles.entrySet()) {
            String file = entry.getKey();
            List<String> conts = entry.getValue().editors;

            if (file.equals("Input") && conts.get(0).trim().length() == 0) {
                // Make a hidden field with blank input
                result.append("<input type='hidden' name='Input' value=''/>");
            } else {
                boolean firstTitle = true;
                int textAreaNumber = 0;
                String appended;
                // int continuingLines = 0;
                boolean editable = true;
                for (String cont : conts) {
                    if (cont == null) { // only the case for the first time to skip editable
                        editable = false;
                    } else {
                        int lines = 0;
                        textAreaNumber++;
                        appended = file + "-" + textAreaNumber;
                        lines = Util.countLines(cont);
                        if (lines == 0)
                            lines = 20;

                        if (editable) {
                            if (firstTitle) {
                                result.append(MessageFormat.format(fileOuterDiv, file));
                                result.append("<h3>");
                                result.append(file);
                                result.append("</h3>");
                                firstTitle = false;
                            }
                            // result.append(MessageFormat.format(startNumberLines, "editor",
                            // "firstLineNumber", continuingLines));
                            result.append(MessageFormat.format(fileAreaBefore, appended, lines, "java"));
                            // TODO support more than "java" in ace editor format
                            result.append(Util
                                    .removeTrailingNewline(Util.htmlEscape(cont)));
                            result.append(fileAreaAfter);
                            editable = false;
                        } else {
                            if (firstTitle) {
                                result.append(MessageFormat.format(fileOuterDiv, file));
                                result.append("<h3>");
                                result.append(file);
                                result.append("</h3>");
                                firstTitle = false;
                            }

                            String s = cont;
                            int max = 20;
                            while (s.indexOf("\n") != -1) {
                                if ((s.substring(0, s.indexOf("\n"))).length() > max) {
                                    max = (s.substring(0, s.indexOf("\n"))).length();
                                }
                                s = s.substring(s.indexOf("\n") + 1);
                            }
                            if (s.length() > max) {
                                max = s.length();
                            }

                            result.append(MessageFormat.format(fileAreaBeforeNoEdit, appended, lines, max, "java")); 
                            // TODO: support more than "java" in ace editor format
                            result.append(Util
                                    .removeTrailingNewline(Util.htmlEscape(cont)));
                            result.append(fileAreaAfter);
                            editable = true;
                        }
                    }
                }
                result.append(fileOuterDivAfter);
            }
        }
        result.append(MessageFormat.format(after, repo, problemName));
        result.append(formEnd);

        int nusefiles = data.useFiles.size();
        if (nusefiles > 0) {
            result.append(MessageFormat.format(useStart, nusefiles));
            for (Map.Entry<String, String> entry : data.useFiles.entrySet()) {
                result.append("<p>");
                result.append(entry.getKey());
                result.append("</p>\n");
                result.append("<pre>");
                result.append(Util.htmlEscape(entry.getValue()));
                result.append("</pre\n>");
            }
        }

        // result.append(jsonpAjaxSubmissionScript);
        result.append(bodyEnd);

            Http.Cookie newCookie = models.Util.buildCookie("ccid", ccid);
        return ok(result.toString()).withCookies(newCookie).as("text/html");
    }       
}
