package services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
// import java.util.Optional;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.script.ScriptException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import models.CodeCheck;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CheckService {
    @Inject private CodeCheck codeCheck;
    ObjectMapper mapper = new ObjectMapper();

    public String[] checkHTMLreport(String newCcid, Map<String, String[]> params) 
            throws NoSuchMethodException, IOException, InterruptedException, ScriptException {
        String ccid = null;
        String repo = "ext";
        String problem = "";
        Map<Path, String> submissionFiles = new TreeMap<>();
        
        for (String key : params.keySet()) {
            String value = params.get(key)[0];
            if (key.equals("repo"))
                repo = value;
            else if (key.equals("problem"))
                problem = value;
            else if (key.equals("ccid")) // TODO: For testing of randomization?
                ccid = value;
            else
                submissionFiles.put(Paths.get(key), value);
        }

        if (ccid == null) {
            ccid = newCcid;
        }
        
        long startTime = System.nanoTime();         
        String report = codeCheck.run("html", repo, problem, ccid, submissionFiles);
        double elapsed = (System.nanoTime() - startTime) / 1000000000.0;
        if (report == null || report.length() == 0) {
            report = String.format("Timed out after %5.0f seconds\n", elapsed);
        }
        return new String[] {ccid, report};
    }
    
    public String run_x_www_form_urlencoded(Map<String, String[]> params, Map<Path, String> submissionFiles) 
            throws NoSuchMethodException, IOException, InterruptedException, ScriptException {
        for (String key : params.keySet()) {
            String value = params.get(key)[0];
            submissionFiles.put(Paths.get(key), value);
        }
        long startTime = System.nanoTime();         
        String report = codeCheck.run("Text", submissionFiles);
        double elapsed = (System.nanoTime() - startTime) / 1000000000.0;
        if (report == null || report.length() == 0) {
            report = String.format("Timed out after %5.0f seconds\n", elapsed);
        }
        return report;
    }

    public String run_form_data(Map<Path, String> submissionFiles) 
            throws NoSuchMethodException, IOException, InterruptedException, ScriptException {
        long startTime = System.nanoTime();         
        String report = codeCheck.run("Text", submissionFiles);
        double elapsed = (System.nanoTime() - startTime) / 1000000000.0;
        if (report == null || report.length() == 0) {
            report = String.format("Timed out after %5.0f seconds\n", elapsed);
        }    
        return report;       
    }

    public ObjectNode run_json(JsonNode json, Map<Path, String> submissionFiles) 
            throws NoSuchMethodException, IOException, InterruptedException, ScriptException {
        Iterator<Entry<String, JsonNode>> iter = json.fields();
        while (iter.hasNext()) {
            Entry<String, JsonNode> entry = iter.next();
            submissionFiles.put(Paths.get(entry.getKey()), entry.getValue().asText());         
        };
        String report = codeCheck.run("JSON", submissionFiles);
        return (ObjectNode) mapper.readTree(report);
    }

    public ObjectNode checkNJSResult(String newCcid, Map<String, String[]> params) throws NoSuchMethodException, IOException, InterruptedException, ScriptException {
        String ccid = null;
        String repo = "ext";
        String problem = null;
        String reportType = "NJS";
        String scoreCallback = null;
        StringBuilder requestParams = new StringBuilder();
        ObjectNode studentWork = JsonNodeFactory.instance.objectNode();
        Map<Path, String> submissionFiles = new TreeMap<>();
        Map<Path, byte[]> reportZipFiles = new TreeMap<>();
        for (String key : params.keySet()) {
            String value = params.get(key)[0];
            
            if (requestParams.length() > 0) requestParams.append(", ");
            requestParams.append(key);
            requestParams.append("=");
            int nl = value.indexOf('\n');
            if (nl >= 0) {
                requestParams.append(value.substring(0, nl));  
                requestParams.append("...");
            }
            else requestParams.append(value);
            
            if ("repo".equals(key)) repo = value;
            else if ("problem".equals(key)) problem = value;
            else if ("scoreCallback".equals(key)) scoreCallback = value;
            else if ("ccid".equals(key)) ccid = value; // TODO: For testing of randomization? 
            else {
                Path p = Paths.get(key);                    
                submissionFiles.put(p, value);
                reportZipFiles.put(p, value.getBytes(StandardCharsets.UTF_8));
                studentWork.put(key, value);
            }
        }
        if (ccid == null) { 
            ccid = newCcid;
        };              
        String report = codeCheck.run(reportType, repo, problem, ccid, submissionFiles);
        ObjectNode result = (ObjectNode) mapper.readTree(report);
        String reportHTML = result.get("report").asText();
        reportZipFiles.put(Paths.get("report.html"), reportHTML.getBytes(StandardCharsets.UTF_8));

        byte[] reportZipBytes = codeCheck.signZip(reportZipFiles);
            
        // TODO Need to sign
        String reportZip = Base64.getEncoder().encodeToString(reportZipBytes); 
        
        //TODO: Score callback no longer used from LTIHub. Does Engage use it?
        if (scoreCallback != null) {
            if (scoreCallback.startsWith("https://")) 
                scoreCallback = "http://" + scoreCallback.substring("https://".length()); // TODO: Fix
            
            //TODO: Add to result the student submissions
            ObjectNode augmentedResult = result.deepCopy();
            augmentedResult.set("studentWork", studentWork);
            
            String resultText = mapper.writeValueAsString(augmentedResult);
            System.getLogger("com.horstmann.codecheck.lti").log(System.Logger.Level.INFO, "Request: " + scoreCallback + " " + resultText);
            String response = com.horstmann.codecheck.Util.httpPost(scoreCallback, resultText, "application/json");
            System.getLogger("com.horstmann.codecheck.lti").log(System.Logger.Level.INFO, "Response: " + response);
        }
        
        result.put("zip", reportZip);
        return result;
    }
}
