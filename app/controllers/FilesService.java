package controllers;

import java.io.IOException;
import java.lang.System.Logger;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptException;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.horstmann.codecheck.Problem;
import com.horstmann.codecheck.Util;

import models.CodeCheck;
import play.mvc.Http;
import play.mvc.Result;

public class FilesService {
    @Inject private CodeCheck codeCheck;    
    private static Logger logger = System.getLogger("com.horstmann.codecheck");
    
    String start2 = "<!DOCTYPE html>\n<html><head>\n"
            + "<title>CodeCheck</title>"
            + "<meta http-equiv='content-type' content='text/html; charset=UTF-8' />\n"
            + "<script src='/assets/download.js'></script>\n" 
            + "<script src='/assets/ace/ace.js'></script>\n"
            + "<script src='/assets/ace/theme-kuroir.js'></script>\n"
            + "<script src='/assets/ace/theme-chrome.js'></script>\n"
            + "<script src='/assets/util.js'></script>\n"
            + "<script src='/assets/codecheck2.js'></script>\n"
            + "<script src='/assets/horstmann_codecheck.js'></script>\n"
            + "<link type='text/css' rel='stylesheet' href='/assets/codecheck.css'/>\n" 
            + "<link type='text/css' rel='stylesheet' href='/assets/horstmann_codecheck.css'/>\n" 
            + "</head><body>\n";
    String mid2 = "<div class='horstmann_codecheck'><script type='text/javascript'>//<![CDATA[\n" 
            + "horstmann_codecheck.setup.push(";
    String end2 = ")\n"  
            + "// ]]>\n"  
            + "</script></div>\n"  
            + "</body>\n" 
            + "</html>";

    public String resultMaker(String url, String repo, String problemName, String ccid) 
            throws IOException, NoSuchMethodException, ScriptException{

        Map<Path, byte[]> problemFiles = getProblemFiles(problemName, repo, ccid);
        if (problemFiles.isEmpty()) // if problemFiles is empty, return "0" to indicate error
            return "0";
        if (problemFiles.containsKey(Path.of("tracer.js")))
        	return tracer2(ccid, problemFiles);

        Problem problem = new Problem(problemFiles);
        ObjectNode data = models.Util.toJson(problem.getProblemData());
        data.put("url",  url + "/checkNJS");
        data.put("repo", repo);
        data.put("problem", problemName);
        String description = "";
        if (data.has("description")) {
            description = data.get("description").asText();
            data.remove("description");
        }
        StringBuilder result = new StringBuilder();
        result.append(start2);
        result.append(description);
        result.append(mid2);
        result.append(data.toString());
        result.append(end2);
        return result.toString();
    }
    
    private static String tracerStart = "<!DOCTYPE html>\n"
            + "<html>\n"
            + "<head>\n"
            + "  <meta charset=\"utf-8\">\n"
            + "  <link href='https://horstmann.com/codecheck/css/codecheck_tracer.css' rel='stylesheet' type='text/css'/>  "
            + "  <title>CodeCheck Tracer</title>\n"
            + "  <script src='/assets/util.js'></script>\n"
            + "  <script src='/assets/codecheck2.js'></script>\n"
            + "</head>\n"
            + "<body>\n";
    private static String tracerScriptStart = "    <div class='codecheck_tracer'>\n"
            + "      <script type='module'>//<![CDATA[\n";
    private static String tracerEnd = "// ]]>\n"
            + "      </script>\n"
            + "  </div>\n"
            + "</body>\n"
            + "</html>";
    
	public String tracer2(String ccid, Map<Path, byte[]> problemFiles) throws IOException {
        if (problemFiles.isEmpty())
            return "0";
		Problem problem = new Problem(problemFiles);
        Problem.DisplayData data = problem.getProblemData();
        StringBuilder result = new StringBuilder();
        result.append(tracerStart);
        if (data.description != null)
            result.append(data.description);
        result.append(tracerScriptStart);
        result.append(Util.getString(problemFiles, Path.of("tracer.js")));
        result.append(tracerEnd);

        return result.toString();
	}

    public Map<Path, byte[]> getProblemFiles (String problemName, String repo, String ccid) {
        
        Map<Path, byte[]> problemFiles;
        try {
            problemFiles = codeCheck.loadProblem(repo, problemName, ccid);
        } catch (Exception e) {
            logger.log(Logger.Level.ERROR, "filesHTML2: Cannot load problem " + repo + "/" + " " + problemName, e);
            // return badRequest("Cannot load problem " + repo + "/" + problemName);
            return new HashMap<>(); // returns empty map if exception
        }
        return problemFiles;
    }
}
