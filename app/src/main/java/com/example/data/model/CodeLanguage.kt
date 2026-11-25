package com.example.data.model

enum class RuntimeCapability {
    ON_DEVICE_WEBVIEW,
    ON_DEVICE_SQLITE,
    ON_DEVICE_VALIDATOR,
    REMOTE_SANDBOX_REQUIRED
}

enum class CodeLanguage(
    val id: String,
    val displayName: String,
    val primaryExtension: String,
    val extensions: List<String>,
    val capability: RuntimeCapability,
    val commentPrefix: String,
    val badgeHex: Long,
    val keywords: List<String>,
    val starterSnippet: String
) {
    HTML(
        id = "html",
        displayName = "HTML",
        primaryExtension = "html",
        extensions = listOf("html", "htm"),
        capability = RuntimeCapability.ON_DEVICE_WEBVIEW,
        commentPrefix = "<!--",
        badgeHex = 0xFFE34F26,
        keywords = listOf("html", "head", "body", "div", "span", "h1", "p", "a", "img", "button", "script", "style", "section", "header", "footer"),
        starterSnippet = "<!DOCTYPE html>\n<html lang=\"th\">\n<head>\n  <meta charset=\"UTF-8\">\n  <title>PoraStudio Web</title>\n</head>\n<body>\n  <h1>สวัสดี PoraStudio</h1>\n</body>\n</html>"
    ),
    CSS(
        id = "css",
        displayName = "CSS",
        primaryExtension = "css",
        extensions = listOf("css"),
        capability = RuntimeCapability.ON_DEVICE_WEBVIEW,
        commentPrefix = "/*",
        badgeHex = 0xFF1572B6,
        keywords = listOf("display", "flex", "grid", "color", "background", "padding", "margin", "border-radius", "font-family", "align-items", "justify-content"),
        starterSnippet = "body {\n  font-family: sans-serif;\n  background: #0b1020;\n  color: #f1f5f9;\n  padding: 24px;\n}"
    ),
    JAVASCRIPT(
        id = "javascript",
        displayName = "JavaScript",
        primaryExtension = "js",
        extensions = listOf("js", "mjs"),
        capability = RuntimeCapability.ON_DEVICE_WEBVIEW,
        commentPrefix = "//",
        badgeHex = 0xFFF7DF1E,
        keywords = listOf("const", "let", "var", "function", "return", "async", "await", "if", "else", "for", "while", "class", "import", "export", "console.log", "try", "catch"),
        starterSnippet = "function greet(name) {\n  return `สวัสดี, \${name}!`;\n}\n\nconsole.log(greet(\"PoraStudio\"));"
    ),
    TYPESCRIPT(
        id = "typescript",
        displayName = "TypeScript",
        primaryExtension = "ts",
        extensions = listOf("ts", "tsx"),
        capability = RuntimeCapability.REMOTE_SANDBOX_REQUIRED,
        commentPrefix = "//",
        badgeHex = 0xFF3178C6,
        keywords = listOf("interface", "type", "const", "let", "function", "return", "async", "await", "enum", "implements", "extends", "readonly", "string", "number", "boolean"),
        starterSnippet = "interface User {\n  id: number;\n  name: string;\n}\n\nconst dev: User = { id: 1, name: \"PoraStudio\" };\nconsole.log(dev);"
    ),
    PYTHON(
        id = "python",
        displayName = "Python",
        primaryExtension = "py",
        extensions = listOf("py"),
        capability = RuntimeCapability.REMOTE_SANDBOX_REQUIRED,
        commentPrefix = "#",
        badgeHex = 0xFF3776AB,
        keywords = listOf("def", "class", "return", "if", "elif", "else", "for", "while", "import", "from", "as", "try", "except", "with", "lambda", "print", "len", "range"),
        starterSnippet = "def fibonacci(n: int) -> list[int]:\n    seq = [0, 1]\n    for _ in range(2, n):\n        seq.append(seq[-1] + seq[-2])\n    return seq[:n]\n\nprint(fibonacci(8))"
    ),
    JAVA(
        id = "java",
        displayName = "Java",
        primaryExtension = "java",
        extensions = listOf("java"),
        capability = RuntimeCapability.REMOTE_SANDBOX_REQUIRED,
        commentPrefix = "//",
        badgeHex = 0xFFED8B00,
        keywords = listOf("public", "private", "class", "static", "void", "int", "String", "boolean", "return", "if", "else", "for", "while", "new", "import", "System.out.println"),
        starterSnippet = "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello from Java!\");\n    }\n}"
    ),
    KOTLIN(
        id = "kotlin",
        displayName = "Kotlin",
        primaryExtension = "kt",
        extensions = listOf("kt", "kts"),
        capability = RuntimeCapability.REMOTE_SANDBOX_REQUIRED,
        commentPrefix = "//",
        badgeHex = 0xFF7F52FF,
        keywords = listOf("fun", "val", "var", "class", "data", "object", "sealed", "interface", "when", "if", "else", "for", "return", "suspend", "println", "listOf"),
        starterSnippet = "data class Task(val id: Int, val title: String)\n\nfun main() {\n    val tasks = listOf(Task(1, \"Learn Kotlin\"))\n    println(tasks)\n}"
    ),
    C(
        id = "c",
        displayName = "C",
        primaryExtension = "c",
        extensions = listOf("c", "h"),
        capability = RuntimeCapability.REMOTE_SANDBOX_REQUIRED,
        commentPrefix = "//",
        badgeHex = 0xFFA8B9CC,
        keywords = listOf("#include", "int", "char", "float", "double", "void", "struct", "return", "if", "else", "for", "while", "printf", "sizeof", "malloc", "free"),
        starterSnippet = "#include <stdio.h>\n\nint main() {\n    printf(\"Hello from C!\\n\");\n    return 0;\n}"
    ),
    CPP(
        id = "cpp",
        displayName = "C++",
        primaryExtension = "cpp",
        extensions = listOf("cpp", "cc", "hpp"),
        capability = RuntimeCapability.REMOTE_SANDBOX_REQUIRED,
        commentPrefix = "//",
        badgeHex = 0xFF00599C,
        keywords = listOf("#include", "using", "namespace", "std", "int", "auto", "class", "struct", "vector", "string", "cout", "endl", "return", "template"),
        starterSnippet = "#include <iostream>\n#include <vector>\n\nint main() {\n    std::cout << \"Hello C++20\" << std::endl;\n    return 0;\n}"
    ),
    CSHARP(
        id = "csharp",
        displayName = "C#",
        primaryExtension = "cs",
        extensions = listOf("cs"),
        capability = RuntimeCapability.REMOTE_SANDBOX_REQUIRED,
        commentPrefix = "//",
        badgeHex = 0xFF512BD4,
        keywords = listOf("using", "namespace", "class", "public", "static", "void", "string", "int", "var", "async", "await", "return", "Console.WriteLine"),
        starterSnippet = "using System;\n\nclass Program {\n    static void Main() {\n        Console.WriteLine(\"Hello from C#!\");\n    }\n}"
    ),
    PHP(
        id = "php",
        displayName = "PHP",
        primaryExtension = "php",
        extensions = listOf("php"),
        capability = RuntimeCapability.REMOTE_SANDBOX_REQUIRED,
        commentPrefix = "//",
        badgeHex = 0xFF777BB4,
        keywords = listOf("<?php", "function", "echo", "return", "if", "else", "foreach", "class", "public", "private", "array", "require"),
        starterSnippet = "<?php\nfunction greet(string \$name): string {\n    return \"Hello, \$name!\";\n}\necho greet(\"PoraStudio\");\n?>"
    ),
    SQL(
        id = "sql",
        displayName = "SQL",
        primaryExtension = "sql",
        extensions = listOf("sql"),
        capability = RuntimeCapability.ON_DEVICE_SQLITE,
        commentPrefix = "--",
        badgeHex = 0xFF336791,
        keywords = listOf("SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "CREATE", "TABLE", "PRIMARY", "KEY", "UPDATE", "SET", "DELETE", "JOIN", "ORDER", "BY", "GROUP"),
        starterSnippet = "CREATE TABLE IF NOT EXISTS developers (\n  id INTEGER PRIMARY KEY AUTOINCREMENT,\n  name TEXT NOT NULL,\n  language TEXT NOT NULL\n);\n\nINSERT INTO developers (name, language) VALUES ('Ananda', 'Kotlin'), ('Ploy', 'Python');\nSELECT * FROM developers;"
    ),
    DART(
        id = "dart",
        displayName = "Dart",
        primaryExtension = "dart",
        extensions = listOf("dart"),
        capability = RuntimeCapability.REMOTE_SANDBOX_REQUIRED,
        commentPrefix = "//",
        badgeHex = 0xFF0175C2,
        keywords = listOf("void", "main", "final", "const", "var", "class", "Future", "async", "await", "List", "Map", "return", "print"),
        starterSnippet = "void main() {\n  final items = ['Dart', 'Flutter'];\n  print('Ready: \$items');\n}"
    ),
    JSON(
        id = "json",
        displayName = "JSON",
        primaryExtension = "json",
        extensions = listOf("json"),
        capability = RuntimeCapability.ON_DEVICE_VALIDATOR,
        commentPrefix = "",
        badgeHex = 0xFF29B6F6,
        keywords = listOf("true", "false", "null"),
        starterSnippet = "{\n  \"name\": \"porastudio-app\",\n  \"version\": \"1.0.0\",\n  \"private\": true\n}"
    ),
    XML(
        id = "xml",
        displayName = "XML",
        primaryExtension = "xml",
        extensions = listOf("xml", "svg"),
        capability = RuntimeCapability.ON_DEVICE_VALIDATOR,
        commentPrefix = "<!--",
        badgeHex = 0xFFF97316,
        keywords = listOf("version", "encoding", "xmlns:android", "layout_width", "layout_height"),
        starterSnippet = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<configuration>\n  <appName>PoraStudio</appName>\n</configuration>"
    ),
    BASH(
        id = "bash",
        displayName = "Bash",
        primaryExtension = "sh",
        extensions = listOf("sh", "bash"),
        capability = RuntimeCapability.REMOTE_SANDBOX_REQUIRED,
        commentPrefix = "#",
        badgeHex = 0xFF4EAA25,
        keywords = listOf("#!/usr/bin/env", "echo", "if", "then", "fi", "for", "in", "do", "done", "export", "function", "exit"),
        starterSnippet = "#!/usr/bin/env bash\necho \"PoraStudio Build Script Ready\""
    );

    companion object {
        fun fromId(id: String): CodeLanguage =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.displayName.equals(id, ignoreCase = true) }
                ?: JAVASCRIPT

        fun fromFileName(fileName: String): CodeLanguage {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { ext in it.extensions } ?: JAVASCRIPT
        }
    }
}
