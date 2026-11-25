package com.example.data.academy

import com.example.data.model.CodeLanguage
import com.example.data.model.CodingChallenge
import com.example.data.model.Course
import com.example.data.model.Lesson
import com.example.data.model.QuizQuestion
import com.example.data.model.SkillLevel
import com.example.data.model.TemplateFileSpec

internal val coursesPart1: List<Course> = listOf(
    Course(
        id = "course_fundamentals",
        titleTh = "พื้นฐานการเขียนโปรแกรม",
        titleEn = "Programming Fundamentals",
        summaryTh = "ตัวแปร เงื่อนไข ลูป ฟังก์ชัน และวิธีคิดแบบนักพัฒนา",
        summaryEn = "Variables, control flow, loops, functions, and computational thinking.",
        primaryLanguage = CodeLanguage.JAVASCRIPT,
        lessons = listOf(
            Lesson(
                id = "fund_1",
                level = SkillLevel.BEGINNER,
                titleTh = "ตัวแปรและชนิดข้อมูล",
                titleEn = "Variables & Data Types",
                explanationTh = "ตัวแปรเปรียบเหมือนกล่องเก็บข้อมูล ใช้ let สำหรับค่าที่เปลี่ยนได้ และ const สำหรับค่าคงที่ ข้อมูลพื้นฐานประกอบด้วย ข้อความ (String), ตัวเลข (Number), และค่าความจริง (Boolean)",
                explanationEn = "Variables store data values. Use const for constants and let for mutable bindings. Core primitives include String, Number, and Boolean.",
                codeExample = "const appName = \"PoraStudio\";\nlet level = 1;\nlet isActive = true;\nconsole.log(appName, \"Level:\", level, isActive);",
                language = CodeLanguage.JAVASCRIPT
            ),
            Lesson(
                id = "fund_2",
                level = SkillLevel.INTERMEDIATE,
                titleTh = "เงื่อนไขและลูป",
                titleEn = "Conditionals & Loops",
                explanationTh = "ใช้ if/else เพื่อตัดสินใจตามเงื่อนไข และใช้ for/while เพื่อทำงานซ้ำอย่างเป็นระบบ",
                explanationEn = "Use if/else branches to make decisions and for/while loops to iterate over ranges or collections.",
                codeExample = "for (let i = 1; i <= 5; i++) {\n  const label = i % 2 === 0 ? \"Even\" : \"Odd\";\n  console.log(i, label);\n}",
                language = CodeLanguage.JAVASCRIPT
            ),
            Lesson(
                id = "fund_3",
                level = SkillLevel.ADVANCED,
                titleTh = "ฟังก์ชันและการแยกโมดูล",
                titleEn = "Functions & Modular Decomposition",
                explanationTh = "ฟังก์ชันที่ดีควรรับ Input ที่ชัดเจน ทำงานเดียวให้สมบูรณ์ และคืนค่า Output ที่ทดสอบได้ง่าย (Pure Function)",
                explanationEn = "Well-designed functions take clear inputs, perform a single responsibility, and return deterministic outputs.",
                codeExample = "function calculateGrade(score) {\n  if (score >= 80) return \"A\";\n  if (score >= 70) return \"B\";\n  return \"C\";\n}\nconsole.log(calculateGrade(85));",
                language = CodeLanguage.JAVASCRIPT
            )
        ),
        challenges = listOf(
            CodingChallenge(
                id = "fund_ch_1",
                titleTh = "สร้างฟังก์ชันคำนวณผลรวม",
                titleEn = "Sum Array Function",
                promptTh = "เขียนฟังก์ชัน sumList(arr) ที่วนลูปบวกเลขในอาเรย์แล้วแสดงผลด้วย console.log",
                promptEn = "Write a sumList(arr) function that sums numbers and logs the result.",
                starterCode = "function sumList(arr) {\n  // TODO\n}\nconsole.log(sumList([10, 20, 30]));",
                expectedSubstring = "return",
                expectedKeywords = listOf("function", "sumList", "for", "return"),
                language = CodeLanguage.JAVASCRIPT
            )
        ),
        quiz = listOf(
            QuizQuestion(
                id = "fund_q1",
                questionTh = "คำสั่งใดใช้ประกาศตัวแปรที่ไม่ต้องการให้เปลี่ยนค่าอ้างอิง?",
                questionEn = "Which keyword declares a constant binding?",
                optionsTh = listOf("var", "let", "const", "loop"),
                optionsEn = listOf("var", "let", "const", "loop"),
                correctIndex = 2,
                explanationTh = "const ใช้ประกาศตัวแปรที่ไม่อนุญาตให้กำหนดค่าอ้างอิงใหม่",
                explanationEn = "const prevents reassignment of the binding."
            )
        ),
        finalProjectTitleTh = "เครื่องคิดเลขเกรดและสถิติ",
        finalProjectTitleEn = "Student Grade & Stats CLI",
        finalProjectBriefTh = "สร้างโปรแกรม JavaScript รับชุดคะแนน คำนวณค่าเฉลี่ย สูงสุด ต่ำสุด และเกรดเฉลี่ย",
        finalProjectBriefEn = "Build a JS calculator computing average, min, max, and letter grades.",
        finalProjectFiles = listOf(
            TemplateFileSpec(
                "grades.js",
                CodeLanguage.JAVASCRIPT,
                "const scores = [88, 74, 92, 65, 81];\nconst avg = scores.reduce((a, b) => a + b, 0) / scores.length;\nconsole.log(\"Average:\", avg.toFixed(2), \"Max:\", Math.max(...scores));"
            )
        )
    ),
    Course(
        id = "course_html_css",
        titleTh = "HTML และ CSS สมัยใหม่",
        titleEn = "HTML & CSS",
        summaryTh = "โครงสร้าง Semantic HTML5, Flexbox, Grid และการออกแบบ Responsive",
        summaryEn = "Semantic HTML5 structure, Flexbox, CSS Grid, and responsive UI design.",
        primaryLanguage = CodeLanguage.HTML,
        lessons = listOf(
            Lesson(
                id = "hc_1",
                level = SkillLevel.BEGINNER,
                titleTh = "โครงสร้าง Semantic HTML5",
                titleEn = "Semantic HTML5 Structure",
                explanationTh = "การใช้แท็กที่มีความหมาย เช่น <header>, <main>, <article>, <footer> ช่วยให้ SEO ดีขึ้นและรองรับผู้อ่านหน้าจอ",
                explanationEn = "Semantic tags like <header>, <main>, <article>, and <footer> improve accessibility and SEO.",
                codeExample = "<main>\n  <article>\n    <h1>บทความแรกของฉัน</h1>\n    <p>เรียนรู้ Semantic HTML5</p>\n  </article>\n</main>",
                language = CodeLanguage.HTML
            ),
            Lesson(
                id = "hc_2",
                level = SkillLevel.INTERMEDIATE,
                titleTh = "เลย์เอาต์ Flexbox และ Box Model",
                titleEn = "Flexbox & Box Model",
                explanationTh = "เข้าใจ content, padding, border, margin และการวางองค์ประกอบแนวนอน/แนวตั้งด้วย display: flex",
                explanationEn = "Master padding, margin, box-sizing, and flexible alignment with display: flex.",
                codeExample = "<div style=\"display:flex;gap:12px;background:#1e293b;padding:16px;border-radius:12px;color:#fff\">\n  <div>Box 1</div>\n  <div>Box 2</div>\n</div>",
                language = CodeLanguage.HTML
            ),
            Lesson(
                id = "hc_3",
                level = SkillLevel.ADVANCED,
                titleTh = "CSS Grid และตัวแปร CSS",
                titleEn = "CSS Grid & Custom Properties",
                explanationTh = "ใช้ CSS Variables (--accent) ร่วมกับ grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)) เพื่อสร้างเว็บที่ยืดหยุ่นทุกขนาดจอ",
                explanationEn = "Combine CSS custom properties with responsive CSS Grid auto-fit tracks.",
                codeExample = "<style>:root{--accent:#38bdf8}.grid{display:grid;grid-template-columns:repeat(2,1fr);gap:10px}.item{padding:12px;border:1px solid var(--accent);color:#fff;background:#0b1020}</style>\n<div class=\"grid\"><div class=\"item\">A</div><div class=\"item\">B</div></div>",
                language = CodeLanguage.HTML
            )
        ),
        challenges = listOf(
            CodingChallenge(
                id = "hc_ch_1",
                titleTh = "สร้างการ์ดโปรไฟล์ด้วย Flexbox",
                titleEn = "Build a Flexbox Profile Card",
                promptTh = "สร้างแท็ก div ที่มี style display: flex และแสดงชื่อของคุณในแท็ก h2",
                promptEn = "Create a div using display: flex and an h2 heading.",
                starterCode = "<div class=\"card\">\n  <h2>Developer</h2>\n</div>",
                expectedSubstring = "flex",
                expectedKeywords = listOf("display", "flex", "<h2"),
                language = CodeLanguage.HTML
            )
        ),
        quiz = listOf(
            QuizQuestion(
                id = "hc_q1",
                questionTh = "คุณสมบัติใดใน CSS ที่ทำให้ padding ไม่ขยายขนาดรวมของกล่องเกิน width ที่กำหนด?",
                questionEn = "Which CSS property keeps padding inside the declared width?",
                optionsTh = listOf("box-sizing: border-box", "display: block", "position: relative", "float: left"),
                optionsEn = listOf("box-sizing: border-box", "display: block", "position: relative", "float: left"),
                correctIndex = 0,
                explanationTh = "box-sizing: border-box รวม border และ padding ไว้ในความกว้าง/สูงที่กำหนด",
                explanationEn = "border-box includes padding and border inside width and height."
            )
        ),
        finalProjectTitleTh = "หน้า Landing Page สินค้าเทคโนโลยี",
        finalProjectTitleEn = "Tech Product Landing Page",
        finalProjectBriefTh = "สร้างหน้าเว็บ Landing Page ที่มี Hero Section, ฟีเจอร์ 3 คอลัมน์ และปุ่ม Call-to-Action",
        finalProjectBriefEn = "Build a responsive landing page with hero section and feature grid.",
        finalProjectFiles = listOf(
            TemplateFileSpec(
                "index.html",
                CodeLanguage.HTML,
                "<!DOCTYPE html><html><head><style>body{font-family:sans-serif;background:#0b1020;color:#f1f5f9;padding:24px}.hero{background:#111827;padding:24px;border-radius:16px;border:1px solid #38bdf8}button{background:#38bdf8;color:#041e2b;border:none;padding:10px 18px;border-radius:10px;font-weight:700}</style></head><body><section class='hero'><h1>PoraStudio Cloud</h1><p>Deploy anywhere in seconds.</p><button>Get Started</button></section></body></html>"
            )
        )
    ),
    Course(
        id = "course_javascript",
        titleTh = "JavaScript แบบเจาะลึก",
        titleEn = "JavaScript",
        summaryTh = "DOM, ES6+, Closures, Promises และ Async/Await",
        summaryEn = "DOM manipulation, ES6+, closures, Promises, and async/await.",
        primaryLanguage = CodeLanguage.JAVASCRIPT,
        lessons = listOf(
            Lesson(
                id = "js_1",
                level = SkillLevel.BEGINNER,
                titleTh = "Array Methods (map, filter, reduce)",
                titleEn = "Functional Array Methods",
                explanationTh = "ใช้ map เพื่อแปลงข้อมูล, filter เพื่อคัดกรอง และ reduce เพื่อรวมผลลัพธ์โดยไม่แก้ไขอาเรย์เดิม",
                explanationEn = "Transform, filter, and aggregate arrays immutably with map, filter, and reduce.",
                codeExample = "const nums = [1, 2, 3, 4, 5];\nconst evensDoubled = nums.filter(n => n % 2 === 0).map(n => n * 2);\nconsole.log(evensDoubled);",
                language = CodeLanguage.JAVASCRIPT
            ),
            Lesson(
                id = "js_2",
                level = SkillLevel.INTERMEDIATE,
                titleTh = "Objects, Destructuring และ Spread",
                titleEn = "Destructuring & Spread Operator",
                explanationTh = "ดึงค่าจาก Object และ Array ได้อย่างกระชับ พร้อมสร้าง Object ใหม่ด้วย ...spread",
                explanationEn = "Extract nested values cleanly and clone/merge objects using spread syntax.",
                codeExample = "const user = { name: \"Niran\", role: \"Dev\" };\nconst { name } = user;\nconst updated = { ...user, level: 5 };\nconsole.log(name, updated);",
                language = CodeLanguage.JAVASCRIPT
            ),
            Lesson(
                id = "js_3",
                level = SkillLevel.ADVANCED,
                titleTh = "Promises และ Async/Await",
                titleEn = "Asynchronous JS & Promises",
                explanationTh = "จัดการงานแบบ Asynchronous อย่างอ่านง่ายด้วย async/await ร่วมกับ try/catch",
                explanationEn = "Handle asynchronous pipelines cleanly with Promises and async/await.",
                codeExample = "async function fetchMock() {\n  const data = await Promise.resolve({ ok: true, items: 3 });\n  console.log(\"Result:\", data);\n}\nfetchMock();",
                language = CodeLanguage.JAVASCRIPT
            )
        ),
        challenges = listOf(
            CodingChallenge(
                id = "js_ch_1",
                titleTh = "กรองผู้ใช้ที่เปิดใช้งาน",
                titleEn = "Filter Active Users",
                promptTh = "ใช้ .filter() และ .map() เพื่อดึงชื่อของผู้ใช้ที่มี active: true",
                promptEn = "Use .filter() and .map() to extract names of active users.",
                starterCode = "const users = [{name:'A', active:true}, {name:'B', active:false}];\n// TODO",
                expectedSubstring = "filter",
                expectedKeywords = listOf("filter", "map", "active"),
                language = CodeLanguage.JAVASCRIPT
            )
        ),
        quiz = listOf(
            QuizQuestion(
                id = "js_q1",
                questionTh = "เมธอดใดคืนค่าเป็นอาเรย์ใหม่ที่มีความยาวเท่าเดิมเสมอ?",
                questionEn = "Which method always returns a new array of the same length?",
                optionsTh = listOf("filter", "map", "find", "reduce"),
                optionsEn = listOf("filter", "map", "find", "reduce"),
                correctIndex = 1,
                explanationTh = "map จะแปลงทุกสมาชิก 1 ต่อ 1 เป็นอาเรย์ใหม่ขนาดเท่าเดิม",
                explanationEn = "map transforms each element 1-to-1 into a same-length array."
            )
        ),
        finalProjectTitleTh = "ระบบตะกร้าสินค้า JS",
        finalProjectTitleEn = "Shopping Cart Engine",
        finalProjectBriefTh = "คำนวณราคารวม ส่วนลด และภาษี 7% ด้วยฟังก์ชัน JavaScript",
        finalProjectBriefEn = "Implement cart totals, discount rules, and tax calculation in JS.",
        finalProjectFiles = listOf(
            TemplateFileSpec(
                "cart.js",
                CodeLanguage.JAVASCRIPT,
                "const cart = [{ name: 'Keyboard', price: 2500, qty: 1 }, { name: 'Mouse', price: 900, qty: 2 }];\nconst subtotal = cart.reduce((s, i) => s + i.price * i.qty, 0);\nconsole.log({ subtotal, vat: subtotal * 0.07, total: subtotal * 1.07 });"
            )
        )
    ),
    Course(
        id = "course_python",
        titleTh = "Python เพื่อการพัฒนาและข้อมูล",
        titleEn = "Python",
        summaryTh = "โครงสร้างข้อมูล List/Dict, OOP, Type Hints และการจัดการไฟล์",
        summaryEn = "Lists, dictionaries, comprehensions, OOP, and clean Pythonic code.",
        primaryLanguage = CodeLanguage.PYTHON,
        lessons = listOf(
            Lesson(
                id = "py_1",
                level = SkillLevel.BEGINNER,
                titleTh = "ไวยากรณ์พื้นฐานและ List Comprehension",
                titleEn = "Syntax & List Comprehensions",
                explanationTh = "Python ใช้การย่อหน้า (Indentation) กำหนดขอบเขตบล็อกโค้ด และมี List Comprehension ที่สั้นและทรงพลัง",
                explanationEn = "Python uses indentation for scope and offers expressive list comprehensions.",
                codeExample = "squares = [x * x for x in range(1, 6)]\nprint(\"Squares:\", squares)",
                language = CodeLanguage.PYTHON
            ),
            Lesson(
                id = "py_2",
                level = SkillLevel.INTERMEDIATE,
                titleTh = "Dictionaries และ Functions",
                titleEn = "Dictionaries & Type Hints",
                explanationTh = "ใช้ dict เก็บคู่ Key-Value และใช้ Type Hints เพื่อให้โค้ดอ่านง่ายและตรวจสอบข้อผิดพลาดได้",
                explanationEn = "Store key-value pairs in dicts and document signatures with type hints.",
                codeExample = "def count_words(text: str) -> dict[str, int]:\n    counts: dict[str, int] = {}\n    for w in text.split():\n        counts[w] = counts.get(w, 0) + 1\n    return counts",
                language = CodeLanguage.PYTHON
            ),
            Lesson(
                id = "py_3",
                level = SkillLevel.ADVANCED,
                titleTh = "Dataclasses และ Exception Handling",
                titleEn = "Dataclasses & Error Handling",
                explanationTh = "ใช้ @dataclass เพื่อลดโค้ดซ้ำซ้อนและใช้ try/except/finally อย่างปลอดภัย",
                explanationEn = "Use @dataclass for clean data models and structured exception handling.",
                codeExample = "from dataclasses import dataclass\n\n@dataclass\nclass Job:\n    id: int\n    status: str = \"pending\"",
                language = CodeLanguage.PYTHON
            )
        ),
        challenges = listOf(
            CodingChallenge(
                id = "py_ch_1",
                titleTh = "สร้างฟังก์ชันกรองเลขคู่",
                titleEn = "Even Number Filter",
                promptTh = "เขียนฟังก์ชัน filter_even(nums) ด้วย List Comprehension",
                promptEn = "Write filter_even(nums) using a Python list comprehension.",
                starterCode = "def filter_even(nums: list[int]) -> list[int]:\n    # TODO\n    pass",
                expectedSubstring = "def filter_even",
                expectedKeywords = listOf("def", "return", "for", "% 2"),
                language = CodeLanguage.PYTHON
            )
        ),
        quiz = listOf(
            QuizQuestion(
                id = "py_q1",
                questionTh = "โครงสร้างข้อมูลใดใน Python ที่เก็บข้อมูลแบบ Key-Value?",
                questionEn = "Which Python built-in stores key-value pairs?",
                optionsTh = listOf("list", "tuple", "dict", "set"),
                optionsEn = listOf("list", "tuple", "dict", "set"),
                correctIndex = 2,
                explanationTh = "dict (dictionary) เก็บข้อมูลแบบ key -> value ด้วยการเข้าถึงแบบ O(1)",
                explanationEn = "dict maps unique hashable keys to values."
            )
        ),
        finalProjectTitleTh = "ตัววิเคราะห์ Log ด้วย Python",
        finalProjectTitleEn = "Python Log Analyzer",
        finalProjectBriefTh = "ออกแบบสคริปต์ Python แยกแยะ Log ระดับ INFO/WARN/ERROR และสรุปสถิติ",
        finalProjectBriefEn = "Write a Python script that parses log lines and summarizes severity counts.",
        finalProjectFiles = listOf(
            TemplateFileSpec(
                "analyzer.py",
                CodeLanguage.PYTHON,
                "logs = [\"INFO started\", \"ERROR db timeout\", \"INFO ready\"]\nsummary = {\"INFO\": 0, \"ERROR\": 0}\nfor line in logs:\n    level = line.split()[0]\n    summary[level] = summary.get(level, 0) + 1\nprint(summary)"
            )
        )
    ),
    Course(
        id = "course_java",
        titleTh = "การเขียนโปรแกรมภาษา Java",
        titleEn = "Java",
        summaryTh = "OOP, Generics, Collections, Streams และ Records",
        summaryEn = "OOP principles, Generics, Collections Framework, Streams, and Records.",
        primaryLanguage = CodeLanguage.JAVA,
        lessons = listOf(
            Lesson(
                id = "java_1",
                level = SkillLevel.BEGINNER,
                titleTh = "คลาสและออบเจกต์ (OOP)",
                titleEn = "Classes & Objects",
                explanationTh = "Java เป็นภาษา OOP แบบ Static Typing ทุกโปรแกรมเริ่มต้นจากคลาสและเมธอด main",
                explanationEn = "Java is strongly typed and object-oriented, anchored by classes and main().",
                codeExample = "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello Java\");\n    }\n}",
                language = CodeLanguage.JAVA
            ),
            Lesson(
                id = "java_2",
                level = SkillLevel.INTERMEDIATE,
                titleTh = "Collections และ Generics",
                titleEn = "Collections & Generics",
                explanationTh = "ใช้ List<T> และ Map<K, V> เพื่อจัดการข้อมูลอย่างปลอดภัยด้านชนิดข้อมูลตั้งแต่ตอนคอมไพล์",
                explanationEn = "Use parameterized List<T> and Map<K,V> for compile-time type safety.",
                codeExample = "import java.util.*;\nList<String> langs = List.of(\"Java\", \"Kotlin\");\nSystem.out.println(langs);",
                language = CodeLanguage.JAVA
            ),
            Lesson(
                id = "java_3",
                level = SkillLevel.ADVANCED,
                titleTh = "Java Streams และ Records",
                titleEn = "Streams API & Records",
                explanationTh = "record ช่วยสร้าง Data Carrier แบบ Immutable และ Stream API ช่วยประมวลผลข้อมูลแบบ Declarative",
                explanationEn = "Combine Java records with Stream pipelines for concise data processing.",
                codeExample = "record User(String name, int age) {}\n// users.stream().filter(u -> u.age() >= 18).toList();",
                language = CodeLanguage.JAVA
            )
        ),
        challenges = listOf(
            CodingChallenge(
                id = "java_ch_1",
                titleTh = "สร้างคลาส Book",
                titleEn = "Create a Book Class",
                promptTh = "สร้าง class Book ที่มีฟิลด์ title และเมธอด getTitle()",
                promptEn = "Define class Book with a title field and getTitle() method.",
                starterCode = "public class Book {\n    // TODO\n}",
                expectedSubstring = "getTitle",
                expectedKeywords = listOf("class", "Book", "String", "return"),
                language = CodeLanguage.JAVA
            )
        ),
        quiz = listOf(
            QuizQuestion(
                id = "java_q1",
                questionTh = "คีย์เวิร์ดใดใช้สืบทอดคลาส (Inheritance) ใน Java?",
                questionEn = "Which keyword inherits from a superclass in Java?",
                optionsTh = listOf("implements", "extends", "inherits", "super"),
                optionsEn = listOf("implements", "extends", "inherits", "super"),
                correctIndex = 1,
                explanationTh = "extends ใช้สืบทอดคลาส ส่วน implements ใช้กับ Interface",
                explanationEn = "extends is used for class inheritance; implements is for interfaces."
            )
        ),
        finalProjectTitleTh = "ระบบจัดการบัญชีธนาคาร Java",
        finalProjectTitleEn = "Java Banking Account Core",
        finalProjectBriefTh = "ออกแบบคลาส Account พร้อมเมธอด deposit, withdraw และการตรวจสอบยอดเงิน",
        finalProjectBriefEn = "Design an Account class with deposit, withdraw, and balance guards.",
        finalProjectFiles = listOf(
            TemplateFileSpec(
                "Main.java",
                CodeLanguage.JAVA,
                "public class Main {\n    static class Account {\n        private double balance = 0;\n        void deposit(double amt) { if (amt > 0) balance += amt; }\n        double getBalance() { return balance; }\n    }\n    public static void main(String[] args) {\n        var acc = new Account();\n        acc.deposit(500);\n        System.out.println(acc.getBalance());\n    }\n}"
            )
        )
    ),
    Course(
        id = "course_c_cpp",
        titleTh = "ภาษา C และ C++",
        titleEn = "C and C++",
        summaryTh = "Pointers, Memory Management, Structs และ Modern C++ STL",
        summaryEn = "Pointers, manual & RAII memory management, structs, and Modern C++ STL.",
        primaryLanguage = CodeLanguage.CPP,
        lessons = listOf(
            Lesson(
                id = "cpp_1",
                level = SkillLevel.BEGINNER,
                titleTh = "พื้นฐาน C และ Pointers",
                titleEn = "C Basics & Pointers",
                explanationTh = "Pointer เก็บที่อยู่หน่วยความจำ (Address) ของตัวแปร ใช้ & เพื่อหา Address และ * เพื่ออ่านค่า",
                explanationEn = "Pointers hold memory addresses. Use & for address-of and * for dereferencing.",
                codeExample = "int x = 42;\nint* ptr = &x;\nprintf(\"Value: %d\\n\", *ptr);",
                language = CodeLanguage.C
            ),
            Lesson(
                id = "cpp_2",
                level = SkillLevel.INTERMEDIATE,
                titleTh = "C++ STL: std::vector และ std::string",
                titleEn = "STL Containers",
                explanationTh = "ใช้ std::vector แทนอาเรย์ดิบเพื่อความปลอดภัยและยืดหยุ่นของหน่วยความจำ",
                explanationEn = "Prefer std::vector over raw C arrays for dynamic sizing and safety.",
                codeExample = "#include <vector>\n#include <iostream>\nstd::vector<int> v = {1, 2, 3};\nfor (int n : v) std::cout << n << \" \";",
                language = CodeLanguage.CPP
            ),
            Lesson(
                id = "cpp_3",
                level = SkillLevel.ADVANCED,
                titleTh = "RAII และ Smart Pointers",
                titleEn = "RAII & Smart Pointers",
                explanationTh = "Modern C++ ใช้ std::unique_ptr และ std::shared_ptr เพื่อป้องกัน Memory Leak โดยอัตโนมัติ",
                explanationEn = "Use std::unique_ptr and RAII so resources are deterministically freed.",
                codeExample = "#include <memory>\nauto ptr = std::make_unique<int>(99);",
                language = CodeLanguage.CPP
            )
        ),
        challenges = listOf(
            CodingChallenge(
                id = "cpp_ch_1",
                titleTh = "วนลูปแสดงค่า std::vector",
                titleEn = "Iterate a Vector",
                promptTh = "สร้าง std::vector<int> และวนลูปแสดงผลด้วย std::cout",
                promptEn = "Create a std::vector<int> and print elements with std::cout.",
                starterCode = "#include <iostream>\n#include <vector>\nint main() {\n    // TODO\n    return 0;\n}",
                expectedSubstring = "vector",
                expectedKeywords = listOf("vector", "cout", "return"),
                language = CodeLanguage.CPP
            )
        ),
        quiz = listOf(
            QuizQuestion(
                id = "cpp_q1",
                questionTh = "Smart Pointer ตัวใดแสดงความเป็นเจ้าของแบบเดี่ยว (Single Ownership)?",
                questionEn = "Which smart pointer enforces single unique ownership?",
                optionsTh = listOf("std::shared_ptr", "std::unique_ptr", "std::weak_ptr", "void*"),
                optionsEn = listOf("std::shared_ptr", "std::unique_ptr", "std::weak_ptr", "void*"),
                correctIndex = 1,
                explanationTh = "std::unique_ptr มีเจ้าของเพียงหนึ่งเดียวและปล่อยหน่วยความจำอัตโนมัติเมื่อพ้น scope",
                explanationEn = "std::unique_ptr owns its resource exclusively and frees it on destruction."
            )
        ),
        finalProjectTitleTh = "เครื่องจัดการคิวงาน C++",
        finalProjectTitleEn = "C++ Task Queue Engine",
        finalProjectBriefTh = "ใช้ std::vector และ struct สร้างคิวงานประสิทธิภาพสูง",
        finalProjectBriefEn = "Build a high-speed task queue using structs and std::vector.",
        finalProjectFiles = listOf(
            TemplateFileSpec(
                "main.cpp",
                CodeLanguage.CPP,
                "#include <iostream>\n#include <vector>\n#include <string>\nstruct Task { int id; std::string title; };\nint main() {\n    std::vector<Task> q = {{1, \"Compile\"}, {2, \"Link\"}};\n    for (const auto& t : q) std::cout << t.id << \": \" << t.title << \"\\n\";\n    return 0;\n}"
            )
        )
    ),
    Course(
        id = "course_kotlin_android",
        titleTh = "Kotlin และพัฒนาแอป Android",
        titleEn = "Kotlin & Android",
        summaryTh = "Null Safety, Data Classes, Coroutines และ Jetpack Compose",
        summaryEn = "Null safety, data classes, Coroutines, Flow, and Jetpack Compose UI.",
        primaryLanguage = CodeLanguage.KOTLIN,
        lessons = listOf(
            Lesson(
                id = "kt_1",
                level = SkillLevel.BEGINNER,
                titleTh = "Null Safety และ Data Class",
                titleEn = "Null Safety & Data Classes",
                explanationTh = "Kotlin แยกชนิด Nullable (String?) ออกจาก Non-null (String) เพื่อป้องกัน NullPointerException",
                explanationEn = "Kotlin's type system distinguishes nullable types (String?) from non-null types.",
                codeExample = "data class Dev(val name: String, val bio: String? = null)\nval d = Dev(\"Kanya\")\nprintln(d.bio ?: \"No bio\")",
                language = CodeLanguage.KOTLIN
            ),
            Lesson(
                id = "kt_2",
                level = SkillLevel.INTERMEDIATE,
                titleTh = "Coroutines และ StateFlow",
                titleEn = "Coroutines & StateFlow",
                explanationTh = "ใช้ suspend functions และ StateFlow ใน ViewModel เพื่อจัดการงาน Background โดยไม่บล็อก UI",
                explanationEn = "Use suspend functions and StateFlow for structured, non-blocking UI state.",
                codeExample = "sealed interface UiState {\n    data object Loading : UiState\n    data class Ready(val items: List<String>) : UiState\n}",
                language = CodeLanguage.KOTLIN
            ),
            Lesson(
                id = "kt_3",
                level = SkillLevel.ADVANCED,
                titleTh = "Jetpack Compose & Material 3",
                titleEn = "Jetpack Compose UI",
                explanationTh = "สร้าง UI แบบ Declarative ด้วย @Composable, remember และ State Hoisting",
                explanationEn = "Build reactive Compose UIs using @Composable functions and state hoisting.",
                codeExample = "@Composable\nfun CounterButton(count: Int, onIncrement: () -> Unit) {\n    Button(onClick = onIncrement) { Text(\"Count: \$count\") }\n}",
                language = CodeLanguage.KOTLIN
            )
        ),
        challenges = listOf(
            CodingChallenge(
                id = "kt_ch_1",
                titleTh = "สร้าง sealed interface UiState",
                titleEn = "Model UiState in Kotlin",
                promptTh = "สร้าง sealed interface UiState ที่มี Loading, Success และ Error",
                promptEn = "Create a sealed interface UiState with Loading, Success, and Error.",
                starterCode = "sealed interface UiState {\n    // TODO\n}",
                expectedSubstring = "sealed interface",
                expectedKeywords = listOf("sealed", "Loading", "Success", "Error"),
                language = CodeLanguage.KOTLIN
            )
        ),
        quiz = listOf(
            QuizQuestion(
                id = "kt_q1",
                questionTh = "ตัวดำเนินการใดใช้กำหนดค่าสำรองเมื่อค่าด้านซ้ายเป็น null (Elvis Operator)?",
                questionEn = "Which operator provides a fallback when the left side is null?",
                optionsTh = listOf("!!", "?.", "?:", "::"),
                optionsEn = listOf("!!", "?.", "?:", "::"),
                correctIndex = 2,
                explanationTh = "?: (Elvis operator) จะคืนค่าด้านขวาหากด้านซ้ายเป็น null",
                explanationEn = "?: returns the right-hand fallback when the left expression evaluates to null."
            )
        ),
        finalProjectTitleTh = "หน้าจอ Notes ด้วย Jetpack Compose",
        finalProjectTitleEn = "Jetpack Compose Notes Module",
        finalProjectBriefTh = "สร้างโมเดล Note และฟังก์ชันจัดการรายการใน Kotlin",
        finalProjectBriefEn = "Build a clean Kotlin data model and state manager for a Notes module.",
        finalProjectFiles = listOf(
            TemplateFileSpec(
                "NotesState.kt",
                CodeLanguage.KOTLIN,
                "data class NoteItem(val id: String, val title: String, val pinned: Boolean = false)\n\nfun main() {\n    val notes = listOf(NoteItem(\"1\", \"Architecture\"), NoteItem(\"2\", \"Compose UI\", true))\n    println(notes.sortedByDescending { it.pinned })\n}"
            )
        )
    ),
    Course(
        id = "course_web_dev",
        titleTh = "การพัฒนาเว็บครบวงจร",
        titleEn = "Web Development",
        summaryTh = "สถาปัตยกรรม Frontend, State, การเชื่อมต่อ API และ Performance",
        summaryEn = "Frontend architecture, state management, fetch APIs, and web performance.",
        primaryLanguage = CodeLanguage.HTML,
        lessons = listOf(
            Lesson(
                id = "web_1",
                level = SkillLevel.BEGINNER,
                titleTh = "สถาปัตยกรรม Client-Server",
                titleEn = "Client-Server Web Architecture",
                explanationTh = "เบราว์เซอร์ส่ง HTTP Request ไปยังเซิร์ฟเวอร์และรับ HTML/JSON กลับมาแสดงผล",
                explanationEn = "Browsers send HTTP requests and render returned HTML, CSS, JS, or JSON payloads.",
                codeExample = "fetch('https://jsonplaceholder.typicode.com/todos/1')\n  .then(r => r.json())\n  .then(d => console.log(d));",
                language = CodeLanguage.JAVASCRIPT
            ),
            Lesson(
                id = "web_2",
                level = SkillLevel.INTERMEDIATE,
                titleTh = "Component-Based UI",
                titleEn = "Reusable Web Components",
                explanationTh = "แยกหน้าเว็บออกเป็นส่วนย่อยที่นำกลับมาใช้ซ้ำได้และดูแลง่าย",
                explanationEn = "Decompose interfaces into reusable, isolated UI components.",
                codeExample = "function Card(title, body) {\n  return `<div class='card'><h3>\${title}</h3><p>\${body}</p></div>`;\n}",
                language = CodeLanguage.JAVASCRIPT
            ),
            Lesson(
                id = "web_3",
                level = SkillLevel.ADVANCED,
                titleTh = "Web Performance & Security (CSP/XSS)",
                titleEn = "Web Performance & XSS Defense",
                explanationTh = "ใช้ textContent แทน innerHTML กับข้อมูลจากผู้ใช้เพื่อป้องกัน Cross-Site Scripting (XSS)",
                explanationEn = "Always sanitize or use textContent instead of innerHTML for untrusted user data.",
                codeExample = "const el = document.createElement('p');\nel.textContent = userInput; // Safe against XSS",
                language = CodeLanguage.JAVASCRIPT
            )
        ),
        challenges = listOf(
            CodingChallenge(
                id = "web_ch_1",
                titleTh = "สร้างข้อความปลอดภัยจาก XSS",
                titleEn = "XSS-Safe DOM Update",
                promptTh = "ใช้ document.createElement และ textContent แสดงข้อความอย่างปลอดภัย",
                promptEn = "Use document.createElement and textContent to safely display text.",
                starterCode = "const p = document.createElement('p');\n// TODO",
                expectedSubstring = "textContent",
                expectedKeywords = listOf("createElement", "textContent"),
                language = CodeLanguage.JAVASCRIPT
            )
        ),
        quiz = listOf(
            QuizQuestion(
                id = "web_q1",
                questionTh = "วิธีใดปลอดภัยที่สุดเมื่อแสดงข้อความที่ผู้ใช้กรอกลงใน DOM?",
                questionEn = "Which DOM property safely inserts untrusted user text without executing HTML?",
                optionsTh = listOf("innerHTML", "outerHTML", "textContent", "document.write"),
                optionsEn = listOf("innerHTML", "outerHTML", "textContent", "document.write"),
                correctIndex = 2,
                explanationTh = "textContent แสดงเป็นข้อความล้วน ไม่ตีความเป็นแท็ก HTML หรือสคริปต์",
                explanationEn = "textContent escapes markup and prevents script injection."
            )
        ),
        finalProjectTitleTh = "แดชบอร์ดเว็บโต้ตอบ",
        finalProjectTitleEn = "Interactive Web Dashboard",
        finalProjectBriefTh = "สร้างหน้าเว็บแดชบอร์ดที่อัปเดตตัวเลขสถิติแบบโต้ตอบได้จริง",
        finalProjectBriefEn = "Build an interactive web dashboard with dynamic DOM state.",
        finalProjectFiles = listOf(
            TemplateFileSpec(
                "index.html",
                CodeLanguage.HTML,
                "<!DOCTYPE html><html><body style='background:#0b1020;color:#fff;font-family:sans-serif;padding:20px'><h2>Web Dashboard</h2><div id='metric'>Visits: 1</div><button onclick=\"document.getElementById('metric').textContent='Visits: ' + (++v)\">Simulate Visit</button><script>let v=1;</script></body></html>"
            )
        )
    )
)
