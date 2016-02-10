package com.horstmann.codecheck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class CppLanguage implements Language {

    @Override
    public boolean isSource(Path p) {
        return p.toString().endsWith(".cpp");
    }

    private static Pattern mainPattern = Pattern.compile("\\s*((int|void)\\s+)?main\\s*\\([^)]*\\)\\s*(\\{\\s*)?");

    /*
     * (non-Javadoc)
     * 
     * @see com.horstmann.codecheck.Language#isMain(java.nio.file.Path,
     * java.nio.file.Path)
     */
    @Override
    public boolean isMain(Path p) {
        if (!isSource(p))
            return false;
        String contents = Util.read(p);
        if (contents == null) return false;
        return mainPattern.matcher(contents).find();
    }

    private String moduleOf(Path path) {
        String name = path.toString();
        if (!name.endsWith(".cpp"))
            return null;
        return name.substring(0, name.length() - 4); // drop .cpp
    }

    private Path pathOf(String moduleName) {
        Path p = FileSystems.getDefault().getPath("", moduleName);
        Path parent = p.getParent();
        if (parent == null)
            return FileSystems.getDefault().getPath(moduleName + ".cpp");
        else
            return parent.resolve(p.getFileName().toString() + ".cpp");
    }

    @Override
    public List<Path> writeTester(Path sourceDir, Path targetDir, Path file,
            List<String> modifiers, String name, List<String> argsList)
            throws IOException {
        
        // function in solution needs to be in separate namespace
        // function of student needs be externed;
        // imports on top
        // Look at line in solution file following //CALL
        // Remove any trailing {, add ;
        // add solution wrapped in namespace solution { ... }
        
        String moduleName = moduleOf(Util.tail(file));
        List<String> lines = Util.readLines(sourceDir.resolve(file));
        int i = 0;
        boolean done = false;
        while (!done) {
            if (i == lines.size()) done = true;
            else {
                String line = lines.get(i).trim();
                if (line.length() == 0 || line.startsWith("#include") || line.startsWith("using ") || line.startsWith("//")) i++;
                else done = true;
            }                
        }
        
        lines.add(i++, "#include \"codecheck.h\"");
        
        lines.add(i++, modifiers.get(0) + " " + modifiers.get(1) + ";"); // extern function from student
        lines.add(i++, "namespace solution {");
        lines.add(i++, modifiers.get(0) + " " + modifiers.get(1) + ";");
        lines.add(i++, "}");
        lines.add(i++, "main(int argc, char *argv[]) {");
        for (int k = 0; k < argsList.size(); k++) {
            lines.add(i++, 
                    "    if (codecheck::eq(argv[1], \"" + (k + 1) + "\")) {");
            lines.add(i++, 
                    "        codecheck::compare(" + name + "(" + argsList.get(k) + "), solution::" + name + "(" + argsList.get(k) + "));");
            lines.add(i++, "}");
        }
        lines.add(i++, "}");
        lines.add(i++, "namespace solution {");
        lines.add("}");
        Files.write(targetDir.resolve(pathOf(moduleName + "CodeCheck")), lines,
                StandardCharsets.UTF_8);
        Files.copy(getClass().getResourceAsStream("codecheck.cpp"), targetDir.resolve("codecheck.cpp"));
        Files.copy(getClass().getResourceAsStream("codecheck.h"), targetDir.resolve("codecheck.h"));
        List<Path> paths = new ArrayList<>();
        paths.add(pathOf(moduleName + "CodeCheck"));
        paths.add(Paths.get("codecheck.cpp"));
        return paths;
    }

    private static String patternString = ".*\\S\\s+([A-Za-z][A-Za-z0-9]*)\\s*=\\s*([^;]+);.*";
    private static Pattern pattern = Pattern.compile(patternString);

    /*
     * (non-Javadoc)
     * 
     * @see com.horstmann.codecheck.Language#variablePattern()
     */
    @Override
    public Pattern variablePattern() {
        return pattern;
    }
    
    @Override
    public List<String> modifiers(String declaration) {
        // We save the type and the rest of the declaration 
        int n = declaration.indexOf("{");
        if (n >= 0) declaration = declaration.substring(0, n);
        n = declaration.indexOf("(") - 1;
        while (n >= 0 && Character.isWhitespace(declaration.charAt(n))) n--;
        while (n >= 0 && !Character.isWhitespace(declaration.charAt(n))) n--;        
        
        return Arrays.asList(declaration.substring(0, n + 1).trim(), declaration.substring(n + 1));
    }
}
