package com.example

import com.example.data.academy.AcademyCatalog
import com.example.data.local.LocalFileEntity
import com.example.data.model.CodeLanguage
import com.example.data.model.ProjectTemplateCatalog
import com.example.data.model.SkillLevel
import com.example.domain.editor.CodeAnalyzer
import com.example.domain.editor.SyntaxHighlighter
import com.example.domain.execution.python.PythonRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeVerseUnitTest {

    @Test
    fun `all 16 programming languages are configured`() {
        assertEquals(16, CodeLanguage.entries.size)
        assertEquals(CodeLanguage.PYTHON, CodeLanguage.fromFileName("app.py"))
        assertEquals(CodeLanguage.TYPESCRIPT, CodeLanguage.fromFileName("index.ts"))
        assertEquals(CodeLanguage.KOTLIN, CodeLanguage.fromFileName("Main.kt"))
        assertEquals(CodeLanguage.CSHARP, CodeLanguage.fromFileName("Program.cs"))
    }

    @Test
    fun `all 15 academy courses have lessons, challenges, quiz, and final projects`() {
        val courses = AcademyCatalog.allCourses
        assertEquals(15, courses.size)
        courses.forEach { course ->
            assertTrue("Missing lessons in ${course.id}", course.lessons.isNotEmpty())
            assertTrue("Missing beginner lesson in ${course.id}", course.lessons.any { it.level == SkillLevel.BEGINNER })
            assertTrue("Missing intermediate lesson in ${course.id}", course.lessons.any { it.level == SkillLevel.INTERMEDIATE })
            assertTrue("Missing advanced lesson in ${course.id}", course.lessons.any { it.level == SkillLevel.ADVANCED })
            assertTrue("Missing challenges in ${course.id}", course.challenges.isNotEmpty())
            assertTrue("Missing quiz in ${course.id}", course.quiz.isNotEmpty())
            assertTrue("Missing final project files in ${course.id}", course.finalProjectFiles.isNotEmpty())
        }
    }

    @Test
    fun `all 9 real project templates exist with starter files`() {
        val templates = ProjectTemplateCatalog.templates
        assertEquals(9, templates.size)
        templates.forEach { template ->
            assertTrue("Empty files in ${template.id}", template.files.isNotEmpty())
        }
    }

    @Test
    fun `code analyzer detects python syntax, html tags, css semicolons, and brackets`() {
        val jsIssues = CodeAnalyzer.analyze("function test() {\n  console.log('hi');\n", CodeLanguage.JAVASCRIPT)
        assertTrue(jsIssues.isNotEmpty())

        val pyIssues = CodeAnalyzer.analyze("def greet()\n  print('hi')", CodeLanguage.PYTHON)
        assertTrue(pyIssues.any { it.messageEn.contains("expected ':'") || it.messageEn.contains("SyntaxError") })

        val pyIndentIssues = CodeAnalyzer.analyze("def greet():\nprint('hi')", CodeLanguage.PYTHON)
        assertTrue(pyIndentIssues.any { it.messageEn.contains("IndentationError") || it.messageEn.contains("expected indented block") })

        val htmlIssues = CodeAnalyzer.analyze("<div><p>Hello</div>", CodeLanguage.HTML)
        assertTrue(htmlIssues.any { it.messageEn.contains("Unclosed tag <p>") })

        val cssIssues = CodeAnalyzer.analyze("body {\n  color: red\n}", CodeLanguage.CSS)
        assertTrue(cssIssues.any { it.messageEn.contains(";") })
    }

    @Test
    fun `code analyzer formats code, detects foldable blocks, auto-indents 3-line braces, and highlights syntax`() {
        val formatted = CodeAnalyzer.formatCode("fun main() {\nprintln(\"hi\")\n}", CodeLanguage.KOTLIN, 2)
        assertEquals("fun main() {\n  println(\"hi\")\n}", formatted)

        val blocks = CodeAnalyzer.detectFoldableBlocks(formatted, CodeLanguage.KOTLIN)
        assertEquals(1, blocks.size)
        assertEquals(1, blocks.first().startLine)
        assertEquals(3, blocks.first().endLine)

        val prev = "fun a() {}"
        val next = "fun a() {\n}"
        val indented = CodeAnalyzer.applyAutoIndentOnEnter(prev, next, "fun a() {\n".length, 2)
        assertNotNull(indented)
        assertEquals("fun a() {\n  \n}", indented?.first)

        val suggestions = CodeAnalyzer.autocompleteSuggestions("pri", 3, CodeLanguage.PYTHON)
        assertTrue(suggestions.contains("print"))

        val samples = mapOf(
            CodeLanguage.PYTHON to "def greet(n):\n    return f'hi {n}' # comment\n",
            CodeLanguage.JAVASCRIPT to "const x = 42;\nconsole.log('ok');",
            CodeLanguage.HTML to "<div class=\"card\"><h1>Hello</h1></div>",
            CodeLanguage.CSS to "body { color: #22d3ee; padding: 12px; }"
        )
        samples.forEach { (lang, snippet) ->
            val styled = SyntaxHighlighter.highlight(snippet, lang)
            assertTrue("Expected spans for $lang", styled.spanStyles.isNotEmpty())
        }
    }

    @Test
    fun `python vm executes arithmetic, f-strings, closures, recursion, comprehensions, slicing, and math`() {
        val code = """
            import math
            def make_adder(k):
                return lambda x: x + k
            add5 = make_adder(5)
            def fib(n):
                if n <= 1:
                    return n
                return fib(n - 1) + fib(n - 2)
            nums = [x * x for x in range(6) if x % 2 == 0]
            rev = nums[::-1]
            squares = {i: i * i for i in range(3)}
            print(f"add={add5(10)}, fib={fib(7)}, rev={rev}, sqrt={int(math.sqrt(81))}, sq={squares[2]}")
        """.trimIndent()
        val outcome = PythonRuntime(timeoutMs = 5_000L, stdinInput = "").execute(code, "main.py")
        assertTrue("Unexpected stderr: ${outcome.stderr}", outcome.isSuccess)
        assertEquals("add=15, fib=13, rev=[16, 4, 0], sqrt=9, sq=4", outcome.stdout)
    }

    @Test
    fun `python vm executes classes with inheritance, exception handling, stdin input, and workspace imports`() {
        val utilsFile = LocalFileEntity(
            id = "u_mod",
            projectId = "p1",
            userId = "u1",
            path = "utils.py",
            name = "utils.py",
            languageId = CodeLanguage.PYTHON.id,
            content = """
                def greet(name):
                    return f"Hello, {name}!"
            """.trimIndent(),
            updatedAtMillis = 0L
        )
        val mainCode = """
            import utils
            class Animal:
                def __init__(self, name):
                    self.name = name
                def speak(self):
                    return "..."
            class Dog(Animal):
                def speak(self):
                    return f"{self.name}: Woof"
            user = input("Name: ")
            d = Dog(user)
            try:
                x = 10 / 0
            except ZeroDivisionError as err:
                msg = "caught"
            finally:
                done = True
            print(utils.greet(user), "|", d.speak(), "|", msg, done)
        """.trimIndent()
        val outcome = PythonRuntime(
            timeoutMs = 5_000L,
            stdinInput = "Niran",
            workspaceFiles = listOf(utilsFile)
        ).execute(mainCode, "main.py")
        assertTrue("Unexpected stderr: ${outcome.stderr}", outcome.isSuccess)
        assertEquals("Name: Hello, Niran! | Niran: Woof | caught True", outcome.stdout)
    }

    @Test
    fun `python vm enforces sandbox security, recursion guard, error tracebacks, and execution timeouts`() {
        // 1. OS / File sandbox block
        val osBlocked = PythonRuntime(2_000L, "").execute("import os", "sec1.py")
        assertFalse(osBlocked.isSuccess)
        assertTrue(osBlocked.stderr.contains("PermissionError"))

        val openBlocked = PythonRuntime(2_000L, "").execute("open('/etc/passwd')", "sec2.py")
        assertFalse(openBlocked.isSuccess)
        assertTrue(openBlocked.stderr.contains("PermissionError"))

        // 2. Runtime traceback with line number
        val divZero = PythonRuntime(2_000L, "").execute("a = 1\nb = 0\nprint(a / b)", "err.py")
        assertFalse(divZero.isSuccess)
        assertTrue(divZero.stderr.contains("line 3") && divZero.stderr.contains("ZeroDivisionError"))

        // 3. Infinite recursion guard
        val recursion = PythonRuntime(2_000L, "").execute("def f():\n    return f()\nf()", "rec.py")
        assertFalse(recursion.isSuccess)
        assertTrue(recursion.stderr.contains("RecursionError"))

        // 4. Infinite loop timeout
        val timeout = PythonRuntime(500L, "").execute("while True:\n    pass", "loop.py")
        assertFalse(timeout.isSuccess)
        assertTrue(timeout.stderr.contains("TimeoutError"))
    }

    @Test
    fun `ai modes and all 11 quick actions have valid Thai and English definitions`() {
        assertEquals(3, com.example.data.repository.AiExplanationMode.entries.size)
        com.example.data.repository.AiExplanationMode.entries.forEach { mode ->
            assertTrue(mode.labelTh.isNotBlank())
            assertTrue(mode.labelEn.isNotBlank())
            assertTrue(mode.systemInstruction.isNotBlank())
        }
        assertEquals(11, com.example.data.repository.AiQuickAction.entries.size)
        com.example.data.repository.AiQuickAction.entries.forEach { action ->
            assertTrue(action.labelTh.isNotBlank())
            assertTrue(action.labelEn.isNotBlank())
            assertTrue(action.promptTemplate.isNotBlank())
        }
    }
}
