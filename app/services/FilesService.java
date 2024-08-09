package services;

import java.io.IOException;
import java.lang.System.Logger;
import java.net.URL;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horstmann.codecheck.Problem;
import com.horstmann.codecheck.Util;

import controllers.Config;
import models.CodeCheck;

public class FilesService {
    @Inject private CodeCheck codeCheck;    
    @Inject private Config config;     
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

    public String filesHTML2(String url, String repo, String problemName, String ccid)
            throws IOException {
        Map<Path, byte[]> problemFiles = getProblemFiles(repo, problemName, ccid);
        if (problemFiles.isEmpty()) // if problemFiles is empty, return null to indicate error
            return null;
        if (problemFiles.containsKey(Path.of("tracer.js")))
        	return tracer(repo, problemName, ccid);

        wakeupChecker();
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
    
	public String tracer(String repo, String problemName, String ccid) throws IOException {
        Map<Path, byte[]> problemFiles = getProblemFiles(repo, problemName, ccid);
        if (problemFiles.isEmpty())
            return null;
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

    public ObjectNode fileData(String repo, String problemName, String ccid) throws IOException {
        Map<Path, byte[]> problemFiles = getProblemFiles(repo, problemName, ccid);
        if (problemFiles.isEmpty()) return null;
        Problem problem = new Problem(problemFiles);
        return models.Util.toJson(problem.getProblemData());
    }

    private Map<Path, byte[]> getProblemFiles(String repo, String problemName, String ccid) {
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

    public String filesHTML(String repo, String problemName, String ccid)
            throws IOException {
        Map<Path, byte[]> problemFiles = getProblemFiles(repo, problemName, ccid);
        if (problemFiles.isEmpty()) // if problemFiles is empty, return null to indicate error
            return null;
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
        return result.toString();
    }
}
