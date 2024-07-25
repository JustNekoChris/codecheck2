package controllers;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.script.ScriptException;

import models.CodeCheck;

public class CheckService {
    @Inject private CodeCheck codeCheck;
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
    
    public String run_x_www_form_urlencoded(Map<String, String[]> params) 
            throws NoSuchMethodException, IOException, InterruptedException, ScriptException {
        Map<Path, String> submissionFiles = new TreeMap<>();
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
    // Additional methods and code can be added here
}
