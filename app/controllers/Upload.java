package controllers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.script.ScriptException;

import com.horstmann.codecheck.Plan;
import com.horstmann.codecheck.Problem;
import com.horstmann.codecheck.Report;
import com.horstmann.codecheck.Util;

import models.CodeCheck;
import play.libs.Files.TemporaryFile;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

public class Upload extends Controller {
    final String repo = "ext";
    @Inject private CodeCheck codeCheck;
    @Inject private UploadService uploadService;

    public Result uploadFiles(Http.Request request) {
        return uploadFiles(request, com.horstmann.codecheck.Util.createPublicUID(), Util.createPrivateUID());
    }

    public Result editedFiles(Http.Request request, String problem, String editKey) {
        try {
            if (uploadService.checkEditKey(problem, editKey))
                return uploadFiles(request, problem, editKey);
            else
                return badRequest("Wrong edit key " + editKey + " in problem " + problem);
        } catch (IOException ex) {
            return badRequest("Problem not found: " + problem);
        } catch (Exception ex) {
            return internalServerError(Util.getStackTrace(ex));
        }
    }

    public Result uploadFiles(Http.Request request, String problem, String editKey) {
        try {
            if (problem == null)
                badRequest("No problem id");
            String response = uploadService.resultUploadFiles(request.body().asFormUrlEncoded(), problem, models.Util.prefix(request), editKey);
            return ok(response).as("text/html").addingToSession(request, "pid", problem);
        } catch (Exception ex) {
            return internalServerError(Util.getStackTrace(ex));
        }
    }
    
    public Result uploadProblem(Http.Request request) {
        return uploadProblem(request, com.horstmann.codecheck.Util.createPublicUID(), Util.createPrivateUID());
    }

    /**
     * Upload of zip file when editing problem with edit key
     */
    public Result editedProblem(Http.Request request, String problem, String editKey) {
        try {
            if (uploadService.checkEditKey(problem, editKey))
                return uploadProblem(request, problem, editKey);
            else
                return badRequest("Wrong edit key " + editKey + " of problem " + problem);
        } catch (IOException ex) {
            return badRequest("Problem not found: " + problem);
        } catch (Exception ex) {
            return internalServerError(Util.getStackTrace(ex));
        }
    }

    public Result uploadProblem(Http.Request request, String problem, String editKey) {
        try {
            play.mvc.Http.MultipartFormData<TemporaryFile> body = request.body().asMultipartFormData();
            if (problem == null)
                badRequest("No problem id");
            Http.MultipartFormData.FilePart<TemporaryFile> tempZipPart = body.getFile("file");
            TemporaryFile tempZipFile = tempZipPart.getRef();
            String response = uploadService.responseUploadProblem(tempZipFile.path(), models.Util.prefix(request), problem, editKey);
            return ok(response).as("text/html").addingToSession(request, "pid", problem);           
        } catch (Exception ex) {
            return internalServerError(Util.getStackTrace(ex));
        }
    }

    public Result editKeySubmit(Http.Request request, String problem, String editKey) {
        if (problem.equals(""))
            return badRequest("No problem id");
        try {
            Map<Path, byte[]> problemFiles = codeCheck.loadProblem(repo, problem);
            Path editKeyPath = Path.of("edit.key");
            if (!problemFiles.containsKey(editKeyPath)) 
                return badRequest("Wrong edit key " + editKey + " for problem " + problem);
            String correctEditKey = new String(problemFiles.get(editKeyPath), StandardCharsets.UTF_8);
            if (!editKey.equals(correctEditKey.trim())) {
                return badRequest("Wrong edit key " + editKey + " for problem " + problem);
            }
            Map<String, String> filesAndContents = new TreeMap<>();
            for (Map.Entry<Path, byte[]> entries : problemFiles.entrySet()) {
                Path p = entries.getKey();
                if (!p.getName(0).toString().equals("_outputs")) {
                    //if (p.getNameCount() == 1)
                        filesAndContents.put(p.toString(), new String(entries.getValue(), StandardCharsets.UTF_8));
                    //else
                    //    return badRequest("Cannot edit problem with directories");
                }
            }
            filesAndContents.remove("edit.key");
            String problemUrl = uploadService.createProblemUrl(models.Util.prefix(request), problem, problemFiles);
            return ok(views.html.edit.render(problem, filesAndContents, correctEditKey, problemUrl));
        } catch (IOException ex) {
            return badRequest("Problem not found: " + problem);
        } catch (Exception ex) {
            return internalServerError(Util.getStackTrace(ex));
        }
    }
}
