package com.example.data.academy

import com.example.data.model.CodeLanguage
import com.example.data.model.CodingChallenge
import com.example.data.model.Course
import com.example.data.model.Lesson
import com.example.data.model.QuizQuestion
import com.example.data.model.SkillLevel
import com.example.data.model.TemplateFileSpec

internal val coursesPart2: List<Course> = listOf(
    Course(
        id = "course_backend",
        titleTh = "การพัฒนา Backend",
        titleEn = "Backend Development",
        summaryTh = "Routing, Middleware, Authentication, Rate Limiting และ Error Handling",
        summaryEn = "Routing, middleware pipelines, auth validation, and resilient services.",
        primaryLanguage = CodeLanguage.TYPESCRIPT,
        lessons = listOf(
            Lesson(
                id = "be_1",
                level = SkillLevel.BEGINNER,
                titleTh = "HTTP Methods และ Status Codes",
                titleEn = "HTTP Methods & Status Codes",
                explanationTh = "ใช้ GET อ่าน, POST สร้าง, PUT/PATCH อัปเดต, DELETE ลบ และตอบกลับ 200, 201, 400, 401, 404, 500 อย่างเหมาะสม",
                explanationEn = "Use semantic HTTP verbs and accurate 2xx/4xx/5xx status codes.",
                codeExample = "type HttpResponse<T> = { status: number; data?: T; error?: string };",
                language = CodeLanguage.TYPESCRIPT
            ),
            Lesson(
                id = "be_2",
                level = SkillLevel.INTERMEDIATE,
                titleTh = "Middleware และ Input Validation",
                titleEn = "Middleware & Input Validation",
                explanationTh = "ตรวจสอบ Schema และสิทธิผู้ใช้งานที่ Middleware ก่อนถึง Business Logic เสมอ",
                explanationEn = "Validate request payloads and auth tokens in middleware before handlers run.",
                codeExample = "function validateId(id: string): boolean {\n  return /^[a-zA-Z0-9_-]{1,64}$/.test(id);\n}",
                language = CodeLanguage.TYPESCRIPT
            ),
            Lesson(
                id = "be_3",
                level = SkillLevel.ADVANCED,
                titleTh = "Idempotency และ Graceful Shutdown",
                titleEn = "Idempotency & Resilience",
                explanationTh = "ออกแบบ API ให้รองรับการเรียกซ้ำอย่างปลอดภัย และปิดการเชื่อมต่อฐานข้อมูลอย่างเรียบร้อยเมื่อปิดเซิร์ฟเวอร์",
                explanationEn = "Support idempotency keys and graceful connection draining on shutdown.",
                codeExample = "const processedKeys = new Set<string>();\nfunction handleOnce(key: string) {\n  if (processedKeys.has(key)) return false;\n  processedKeys.add(key);\n  return true;\n}",
                language = CodeLanguage.TYPESCRIPT
            )
        ),
        challenges = listOf(
            CodingChallenge(
                id = "be_ch_1",
                titleTh = "สร้าง Validator ตรวจสอบ Input",
                titleEn = "Request Input Validator",
                promptTh = "เขียนฟังก์ชัน validatePayload(name: string) ที่คืนค่า false ถ้าว่างหรือยาวเกิน 100",
                promptEn = "Write validatePayload(name: string) enforcing length 1..100.",
                starterCode = "function validatePayload(name: string): boolean {\n  // TODO\n  return false;\n}",
                expectedSubstring = "length",
                expectedKeywords = listOf("trim", "length", "100"),
                language = CodeLanguage.TYPESCRIPT
            )
        ),
        quiz = listOf(
            QuizQuestion(
                id = "be_q1",
                questionTh = "HTTP Status Code ใดเหมาะสมที่สุดเมื่อสร้าง Resource ใหม่สำเร็จ?",
                questionEn = "Which HTTP status code indicates a resource was newly created?",
                optionsTh = listOf("200 OK", "201 Created", "400 Bad Request", "403 Forbidden"),
                optionsEn = listOf("200 OK", "201 Created", "400 Bad Request", "403 Forbidden"),
                correctIndex = 1,
                explanationTh = "201 Created ใช้ยืนยันว่าเซิร์ฟเวอร์ได้สร้าง Resource ใหม่เรียบร้อยแล้ว",
                explanationEn = "201 Created confirms a new resource has been created."
            )
        ),
        finalProjectTitleTh = "Backend Service TypeScript",
        finalProjectTitleEn = "TypeScript Backend Handler",
        finalProjectBriefTh = "สร้างชุด Handler สำหรับจัดการ Task พร้อมการตรวจสอบ Input",
        finalProjectBriefEn = "Build a typed backend service handler with strict input validation.",
        finalProjectFiles = listOf(
            TemplateFileSpec(
                "service.ts",
                CodeLanguage.TYPESCRIPT,
                "export interface TaskDTO { id: string; title: string; }\nexport function createTask(title: string): TaskDTO {\n  if (!title.trim()) throw new Error('Title required');\n  return { id: 't_1', title: title.trim() };\n}"
            )
        )
    ),
    Course(
        id = "course_database",
        titleTh = "การออกแบบและพัฒนาฐานข้อมูล",
        titleEn = "Database Development",
        summaryTh = "SQL, Normalization, Joins, Indexes และ NoSQL Modeling",
        summaryEn = "Relational SQL, normalization, joins, indexing, and NoSQL document modeling.",
        primaryLanguage = CodeLanguage.SQL,
        lessons = listOf(
            Lesson(
                id = "db_1",
                level = SkillLevel.BEGINNER,
                titleTh = "CREATE TABLE, INSERT และ SELECT",
                titleEn = "Tables, Inserts & Queries",
                explanationTh = "กำหนด Primary Key และ Constraint เช่น NOT NULL / UNIQUE เพื่อรักษาความถูกต้องของข้อมูล",
                explanationEn = "Define primary keys and NOT NULL / UNIQUE constraints for data integrity.",
                codeExample = "CREATE TABLE IF NOT EXISTS users (\n  id INTEGER PRIMARY KEY AUTOINCREMENT,\n  name TEXT NOT NULL,\n  email TEXT UNIQUE NOT NULL\n);",
                language = CodeLanguage.SQL
            ),
            Lesson(
                id = "db_2",
                level = SkillLevel.INTERMEDIATE,
                titleTh = "JOIN และ Aggregation",
                titleEn = "Joins & Aggregations",
                explanationTh = "เชื่อมข้อมูลหลายตารางด้วย INNER JOIN / LEFT JOIN และสรุปผลด้วย GROUP BY",
                explanationEn = "Combine normalized tables with JOINs and summarize using GROUP BY.",
                codeExample = "SELECT language, COUNT(*) AS total FROM developers GROUP BY language ORDER BY total DESC;",
                language = CodeLanguage.SQL
            ),
            Lesson(
                id = "db_3",
                level = SkillLevel.ADVANCED,
                titleTh = "Indexes, Transactions และ NoSQL Parity",
                titleEn = "Indexes & Transactions",
                explanationTh = "สร้าง Index บนคอลัมน์ที่ค้นหาบ่อย และใช้ Transaction เพื่อให้ชุดคำสั่งสำเร็จพร้อมกันทั้งหมด (ACID)",
                explanationEn = "Add indexes on hot query columns and wrap multi-step writes in ACID transactions.",
                codeExample = "CREATE INDEX IF NOT EXISTS idx_products_sku ON products(sku);",
                language = CodeLanguage.SQL
            )
        ),
        challenges = listOf(
            CodingChallenge(
                id = "db_ch_1",
                titleTh = "คิวรีรายการสินค้าที่พร้อมขาย",
                titleEn = "Query In-Stock Products",
                promptTh = "เขียนคำสั่ง SELECT เพื่อดึงสินค้าที่มี stock > 0 เรียงจากราคาสูงสุด",
                promptEn = "Write a SELECT query for stock > 0 ordered by price DESC.",
                starterCode = "SELECT name, price FROM products\n-- TODO",
                expectedSubstring = "ORDER BY",
                expectedKeywords = listOf("SELECT", "WHERE", "stock", "ORDER BY"),
                language = CodeLanguage.SQL
            )
        ),
        quiz = listOf(
            QuizQuestion(
                id = "db_q1",
                questionTh = "คุณสมบัติใดใน ACID ที่รับประกันว่าชุดคำสั่งใน Transaction จะสำเร็จทั้งหมดหรือยกเลิกทั้งหมด?",
                questionEn = "Which ACID property guarantees all-or-nothing execution?",
                optionsTh = listOf("Atomicity", "Consistency", "Isolation", "Durability"),
                optionsEn = listOf("Atomicity", "Consistency", "Isolation", "Durability"),
                correctIndex = 0,
                explanationTh = "Atomicity ทำให้ Transaction สำเร็จสมบูรณ์ทั้งหมดหรือ Rollback กลับทั้งหมด",
                explanationEn = "Atomicity ensures a transaction either commits completely or rolls back."
            )
        ),
        finalProjectTitleTh = "ฐานข้อมูลร้านค้า SQLite",
        finalProjectTitleEn = "E-Commerce SQLite Schema",
        finalProjectBriefTh = "ออกแบบตาราง customers, orders และคิวรีสรุปยอดขายจริงบน SQLite",
        finalProjectBriefEn = "Design customers and orders tables and run real SQLite analytics.",
        finalProjectFiles = listOf(
            TemplateFileSpec(
                "store.sql",
                CodeLanguage.SQL,
                "CREATE TABLE IF NOT EXISTS orders (id INTEGER PRIMARY KEY AUTOINCREMENT, customer TEXT NOT NULL, amount REAL NOT NULL);\nINSERT INTO orders (customer, amount) VALUES ('Somchai', 1200), ('Suda', 3400);\nSELECT customer, SUM(amount) AS spent FROM orders GROUP BY customer;"
            )
        )
    ),
    Course(
        id = "course_api",
        titleTh = "การออกแบบและทดสอบ API",
        titleEn = "API Development",
        summaryTh = "RESTful Design, JSON Schemas, Versioning, Pagination และ Security",
        summaryEn = "RESTful contracts, JSON schemas, pagination, auth headers, and testing.",
        primaryLanguage = CodeLanguage.JSON,
        lessons = listOf(
            Lesson(
                id = "api_1",
                level = SkillLevel.BEGINNER,
                titleTh = "โครงสร้าง Request / Response JSON",
                titleEn = "REST JSON Payloads",
                explanationTh = "ออกแบบ JSON ให้สม่ำเสมอ ใช้ camelCase หรือ snake_case ให้เป็นมาตรฐานเดียวกันทั้ง API",
                explanationEn = "Design consistent JSON response envelopes with predictable field types.",
                codeExample = "{\n  \"status\": \"ok\",\n  \"data\": { \"id\": 101, \"title\": \"API Contract\" },\n  \"meta\": { \"page\": 1, \"limit\": 20 }\n}",
                language = CodeLanguage.JSON
            ),
            Lesson(
                id = "api_2",
                level = SkillLevel.INTERMEDIATE,
                titleTh = "Pagination และ Filtering",
                titleEn = "Pagination & Filtering",
                explanationTh = "ใช้ cursor หรือ limit/offset เพื่อลดภาระเครือข่ายและป้องกันการดึงข้อมูลขนาดใหญ่เกินไป",
                explanationEn = "Paginate list endpoints with cursor or limit/offset parameters.",
                codeExample = "{\n  \"endpoint\": \"/v1/projects?limit=20&cursor=abc\",\n  \"hasMore\": true\n}",
                language = CodeLanguage.JSON
            ),
            Lesson(
                id = "api_3",
                level = SkillLevel.ADVANCED,
                titleTh = "Rate Limiting & Bearer Auth",
                titleEn = "API Security & Rate Limits",
                explanationTh = "ส่ง Token ผ่าน Authorization: Bearer เสมอ ไม่ส่งความลับใน URL Query String",
                explanationEn = "Pass credentials in Authorization headers, never in URL query strings.",
                codeExample = "{\n  \"headers\": { \"Authorization\": \"Bearer <token>\" },\n  \"rateLimitRemaining\": 59\n}",
                language = CodeLanguage.JSON
            )
        ),
        challenges = listOf(
            CodingChallenge(
                id = "api_ch_1",
                titleTh = "ออกแบบ JSON Schema สำหรับ Error",
                titleEn = "Standard Error Payload",
                promptTh = "สร้าง JSON object ที่มีฟิลด์ errorCode, message และ timestamp",
                promptEn = "Create a valid JSON object containing errorCode, message, and timestamp.",
                starterCode = "{\n  \"errorCode\": \"INVALID_INPUT\"\n}",
                expectedSubstring = "message",
                expectedKeywords = listOf("errorCode", "message", "timestamp"),
                language = CodeLanguage.JSON
            )
        ),
        quiz = listOf(
            QuizQuestion(
                id = "api_q1",
                questionTh = "เหตุใดจึงไม่ควรส่ง API Key หรือ Token ผ่าน URL Query Parameter?",
                questionEn = "Why should tokens never be passed in URL query parameters?",
                optionsTh = listOf("URL ยาวเกิน 10 ตัวอักษร", "อาจถูกบันทึกใน Server/Proxy Logs และ Browser History", "JSON ไม่รองรับ URL", "ทำให้ Status เป็น 404"),
                optionsEn = listOf("URLs are limited to 10 chars", "They leak into server/proxy logs and browser history", "JSON cannot parse URLs", "It forces 404 errors"),
                correctIndex = 1,
                explanationTh = "Query string มักถูกเก็บใน log ของเว็บเซิร์ฟเวอร์และประวัติเบราว์เซอร์ ทำให้ความลับรั่วไหลได้",
                explanationEn = "Query strings are commonly logged in plaintext by proxies and servers."
            )
        ),
        finalProjectTitleTh = "OpenAPI JSON Spec",
        finalProjectTitleEn = "REST API Contract JSON",
        finalProjectBriefTh = "สร้างไฟล์สเปก JSON และนำไปทดสอบยิงจริงในแท็บ API Tester",
        finalProjectBriefEn = "Author a JSON payload spec and test live endpoints in API Tester.",
        finalProjectFiles = listOf(
            TemplateFileSpec(
                "api_contract.json",
                CodeLanguage.JSON,
                "{\n  \"openapi\": \"3.1.0\",\n  \"info\": { \"title\": \"PoraStudio Tasks API\", \"version\": \"1.0.0\" },\n  \"paths\": { \"/todos/1\": { \"get\": { \"summary\": \"Get sample todo\" } } }\n}"
            )
        )
    ),
    Course(
        id = "course_app_dev",
        titleTh = "สถาปัตยกรรมพัฒนาแอปพลิเคชัน",
        titleEn = "App Development",
        summaryTh = "MVVM, Clean Architecture, Offline-First และ Navigation",
        summaryEn = "MVVM, Clean Architecture, offline-first sync, and mobile UX.",
        primaryLanguage = CodeLanguage.KOTLIN,
        lessons = listOf(
            Lesson(
                id = "app_1",
                level = SkillLevel.BEGINNER,
                titleTh = "แยกชั้น UI, ViewModel และ Repository",
                titleEn = "Layered App Architecture",
                explanationTh = "UI แสดงผลและส่ง Event, ViewModel ถือ State, Repository จัดการแหล่งข้อมูล (Room/Cloud)",
                explanationEn = "Separate UI rendering, ViewModel state holders, and Repository data sources.",
                codeExample = "class Repo {\n    fun loadItems(): List<String> = listOf(\"Offline\", \"Cloud\")\n}",
                language = CodeLanguage.KOTLIN
            ),
            Lesson(
                id = "app_2",
                level = SkillLevel.INTERMEDIATE,
                titleTh = "Offline-First Synchronization",
                titleEn = "Offline-First Strategy",
                explanationTh = "บันทึกลงฐานข้อมูลในเครื่องทันทีเพื่อให้ใช้งานได้แม้ไม่มีเน็ต แล้วค่อยซิงค์ขึ้น Cloud เมื่อออนไลน์",
                explanationEn = "Persist locally first for zero-latency offline UX, then sync to cloud.",
                codeExample = "data class SyncableItem(val id: String, val synced: Boolean)",
                language = CodeLanguage.KOTLIN
            ),
            Lesson(
                id = "app_3",
                level = SkillLevel.ADVANCED,
                titleTh = "Adaptive Layout (Phone & Tablet)",
                titleEn = "Adaptive Multi-Screen UI",
                explanationTh = "ใช้ WindowWidthSizeClass สลับ NavigationBar กับ NavigationRail สำหรับแท็บเล็ตและหน้าจอกว้าง",
                explanationEn = "Adapt navigation chrome dynamically between NavigationBar and NavigationRail.",
                codeExample = "val useRail = availableWidthDp >= 600",
                language = CodeLanguage.KOTLIN
            )
        ),
        challenges = listOf(
            CodingChallenge(
                id = "app_ch_1",
                titleTh = "ออกแบบ Repository Interface",
                titleEn = "Design a Repository Interface",
                promptTh = "สร้าง interface TaskRepository ที่มี suspend fun fetchTasks()",
                promptEn = "Define interface TaskRepository with suspend fun fetchTasks().",
                starterCode = "interface TaskRepository {\n    // TODO\n}",
                expectedSubstring = "suspend fun",
                expectedKeywords = listOf("interface", "suspend", "fetchTasks"),
                language = CodeLanguage.KOTLIN
            )
        ),
        quiz = listOf(
            QuizQuestion(
                id = "app_q1",
                questionTh = "ชั้นใดในสถาปัตยกรรม MVVM ที่ไม่ควรเข้าถึง SQLite/DAO โดยตรง?",
                questionEn = "Which layer should NEVER call a Room DAO directly?",
                optionsTh = listOf("Repository", "Composable UI", "DataSource", "Database"),
                optionsEn = listOf("Repository", "Composable UI", "DataSource", "Database"),
                correctIndex = 1,
                explanationTh = "UI Composable ควรรับ State จาก ViewModel เท่านั้น ไม่ควรเรียก DAO โดยตรง",
                explanationEn = "UI Composables should observe ViewModel state rather than calling DAOs directly."
            )
        ),
        finalProjectTitleTh = "โมดูล Offline Sync",
        finalProjectTitleEn = "Offline-First Sync Module",
        finalProjectBriefTh = "ออกแบบ Model และ Repository สำหรับซิงค์ข้อมูลสองทาง",
        finalProjectBriefEn = "Design a clean offline-first repository contract in Kotlin.",
        finalProjectFiles = listOf(
            TemplateFileSpec(
                "SyncModule.kt",
                CodeLanguage.KOTLIN,
                "data class SyncRecord(val id: String, val updatedAt: Long, val dirty: Boolean)\nfun pendingRecords(list: List<SyncRecord>) = list.filter { it.dirty }"
            )
        )
    ),
    Course(
        id = "course_swe",
        titleTh = "วิศวกรรมซอฟต์แวร์",
        titleEn = "Software Engineering",
        summaryTh = "SOLID Principles, Clean Code, Testing Pyramid และ Code Review",
        summaryEn = "SOLID principles, clean code, automated testing, and code reviews.",
        primaryLanguage = CodeLanguage.JAVA,
        lessons = listOf(
            Lesson(
                id = "swe_1",
                level = SkillLevel.BEGINNER,
                titleTh = "Clean Code และการตั้งชื่อ",
                titleEn = "Clean Naming & Readability",
                explanationTh = "ตั้งชื่อตัวแปรและฟังก์ชันให้สื่อเจตนาชัดเจน ลดความจำเป็นในการเขียนคอมเมนต์อธิบายโค้ดที่ซับซ้อน",
                explanationEn = "Choose intention-revealing names so code reads like clear prose.",
                codeExample = "int elapsedDaysSinceUpdate = 3; // Clearer than int d = 3;",
                language = CodeLanguage.JAVA
            ),
            Lesson(
                id = "swe_2",
                level = SkillLevel.INTERMEDIATE,
                titleTh = "หลักการ SOLID",
                titleEn = "SOLID Design Principles",
                explanationTh = "Single Responsibility และ Dependency Inversion ช่วยให้โค้ดเปลี่ยนส่วนประกอบและเขียน Unit Test ได้ง่าย",
                explanationEn = "Single Responsibility and Dependency Inversion decouple modules for easy testing.",
                codeExample = "interface Notifier { void send(String msg); }",
                language = CodeLanguage.JAVA
            ),
            Lesson(
                id = "swe_3",
                level = SkillLevel.ADVANCED,
                titleTh = "Testing Pyramid & CI/CD",
                titleEn = "Testing Pyramid & CI/CD",
                explanationTh = "เน้น Unit Test จำนวนมากที่รันเร็ว ผสาน Integration Test และ Automated Lint ใน Pipeline",
                explanationEn = "Balance fast unit tests at the base with targeted integration tests.",
                codeExample = "// Arrange -> Act -> Assert\n// assertEquals(expected, actual);",
                language = CodeLanguage.JAVA
            )
        ),
        challenges = listOf(
            CodingChallenge(
                id = "swe_ch_1",
                titleTh = "แยก Interface เพื่อ Dependency Inversion",
                titleEn = "Apply Dependency Inversion",
                promptTh = "สร้าง interface PaymentGateway และคลาส CheckoutService ที่รับ interface ผ่าน Constructor",
                promptEn = "Create PaymentGateway interface injected into CheckoutService.",
                starterCode = "interface PaymentGateway { boolean charge(double amount); }\nclass CheckoutService {\n    // TODO\n}",
                expectedSubstring = "PaymentGateway",
                expectedKeywords = listOf("interface", "CheckoutService", "PaymentGateway"),
                language = CodeLanguage.JAVA
            )
        ),
        quiz = listOf(
            QuizQuestion(
                id = "swe_q1",
                questionTh = "ตัวอักษร 'S' ใน SOLID ย่อมาจากหลักการใด?",
                questionEn = "What does the 'S' in SOLID stand for?",
                optionsTh = listOf("Static Typing", "Single Responsibility Principle", "Singleton Pattern", "Serverless"),
                optionsEn = listOf("Static Typing", "Single Responsibility Principle", "Singleton Pattern", "Serverless"),
                correctIndex = 1,
                explanationTh = "Single Responsibility Principle: คลาสหรือโมดูลหนึ่งควรมีเหตุผลในการเปลี่ยนแปลงเพียงหนึ่งเดียว",
                explanationEn = "A class or module should have one, and only one, reason to change."
            )
        ),
        finalProjectTitleTh = "โครงสร้าง Service ตามหลัก SOLID",
        finalProjectTitleEn = "SOLID Service Refactor",
        finalProjectBriefTh = "ออกแบบ Service ที่ฉีด Dependency ผ่าน Constructor และทดสอบได้",
        finalProjectBriefEn = "Design a dependency-injected service following SOLID principles.",
        finalProjectFiles = listOf(
            TemplateFileSpec(
                "OrderService.java",
                CodeLanguage.JAVA,
                "public class OrderService {\n    public interface Logger { void log(String s); }\n    private final Logger logger;\n    public OrderService(Logger logger) { this.logger = logger; }\n    public void placeOrder(String id) { logger.log(\"Placed \" + id); }\n}"
            )
        )
    ),
    Course(
        id = "course_dsa",
        titleTh = "อัลกอริทึมและโครงสร้างข้อมูล",
        titleEn = "Algorithms & Data Structures",
        summaryTh = "Big-O, Stack/Queue, HashMap, Trees, Binary Search และ DP",
        summaryEn = "Big-O complexity, stacks, queues, hash maps, trees, and binary search.",
        primaryLanguage = CodeLanguage.JAVASCRIPT,
        lessons = listOf(
            Lesson(
                id = "dsa_1",
                level = SkillLevel.BEGINNER,
                titleTh = "Big-O Notation และ Time Complexity",
                titleEn = "Big-O & Complexity",
                explanationTh = "วัดประสิทธิภาพอัลกอริทึมตามขนาดข้อมูล: O(1) คงที่, O(log n) ค้นหาแบบทวิภาค, O(n) เชิงเส้น, O(n^2) ซ้อนลูป",
                explanationEn = "Analyze growth rate: O(1) constant, O(log n) binary search, O(n) linear, O(n^2) quadratic.",
                codeExample = "// O(1) lookup via Set/Map vs O(n) array scan\nconst seen = new Set([10, 20, 30]);\nconsole.log(seen.has(20));",
                language = CodeLanguage.JAVASCRIPT
            ),
            Lesson(
                id = "dsa_2",
                level = SkillLevel.INTERMEDIATE,
                titleTh = "Binary Search และ Two Pointers",
                titleEn = "Binary Search",
                explanationTh = "Binary Search ลดเวลาค้นหาบนข้อมูลที่เรียงแล้วจาก O(n) เหลือ O(log n)",
                explanationEn = "Binary search cuts sorted search space in half each step: O(log n).",
                codeExample = "function binarySearch(arr, target) {\n  let lo = 0, hi = arr.length - 1;\n  while (lo <= hi) {\n    const mid = Math.floor((lo + hi) / 2);\n    if (arr[mid] === target) return mid;\n    if (arr[mid] < target) lo = mid + 1; else hi = mid - 1;\n  }\n  return -1;\n}\nconsole.log(binarySearch([2, 5, 8, 12, 16], 12));",
                language = CodeLanguage.JAVASCRIPT
            ),
            Lesson(
                id = "dsa_3",
                level = SkillLevel.ADVANCED,
                titleTh = "BFS / DFS Graph Traversal",
                titleEn = "Graph BFS & DFS",
                explanationTh = "ใช้ Queue สำหรับ Breadth-First Search (หาทางสั้นสุด) และ Stack/Recursion สำหรับ Depth-First Search",
                explanationEn = "Use a queue for BFS shortest-path discovery and recursion/stack for DFS.",
                codeExample = "const graph = { A: ['B', 'C'], B: ['D'], C: [], D: [] };\nconst q = ['A'], visited = new Set(['A']);\nwhile (q.length) {\n  const node = q.shift();\n  console.log(node);\n  for (const nxt of graph[node]) if (!visited.has(nxt)) { visited.add(nxt); q.push(nxt); }\n}",
                language = CodeLanguage.JAVASCRIPT
            )
        ),
        challenges = listOf(
            CodingChallenge(
                id = "dsa_ch_1",
                titleTh = "แก้โจทย์ Two Sum ด้วย Hash Map",
                titleEn = "Two Sum in O(n)",
                promptTh = "เขียนฟังก์ชัน twoSum(nums, target) ที่ใช้ Map เพื่อหา index สองตัวใน O(n)",
                promptEn = "Implement twoSum(nums, target) in O(n) using a Map.",
                starterCode = "function twoSum(nums, target) {\n  const seen = new Map();\n  // TODO\n}\nconsole.log(twoSum([2, 7, 11, 15], 9));",
                expectedSubstring = "seen.has",
                expectedKeywords = listOf("Map", "has", "set", "return"),
                language = CodeLanguage.JAVASCRIPT
            )
        ),
        quiz = listOf(
            QuizQuestion(
                id = "dsa_q1",
                questionTh = "Time Complexity ของ Binary Search บนอาเรย์ที่เรียงลำดับขนาด n คือข้อใด?",
                questionEn = "What is the worst-case time complexity of Binary Search?",
                optionsTh = listOf("O(1)", "O(log n)", "O(n)", "O(n log n)"),
                optionsEn = listOf("O(1)", "O(log n)", "O(n)", "O(n log n)"),
                correctIndex = 1,
                explanationTh = "Binary Search ตัดพื้นที่ค้นหาออกครึ่งหนึ่งในแต่ละรอบ จึงเป็น O(log n)",
                explanationEn = "Each comparison halves the remaining search interval."
            )
        ),
        finalProjectTitleTh = "ชุดทดสอบอัลกอริทึม JS",
        finalProjectTitleEn = "Algorithm Benchmark Suite",
        finalProjectBriefTh = "รันการเปรียบเทียบ Binary Search และ BFS จริงใน JS Runtime",
        finalProjectBriefEn = "Run live Binary Search and Graph BFS benchmarks in JS.",
        finalProjectFiles = listOf(
            TemplateFileSpec(
                "algo.js",
                CodeLanguage.JAVASCRIPT,
                "function binarySearch(a, t){let l=0,r=a.length-1;while(l<=r){const m=(l+r)>>1;if(a[m]===t)return m;if(a[m]<t)l=m+1;else r=m-1;}return -1;}\nconsole.log('Index of 42:', binarySearch([1,7,15,42,99], 42));"
            )
        )
    ),
    Course(
        id = "course_git_github",
        titleTh = "Git และ GitHub มืออาชีพ",
        titleEn = "Git & GitHub",
        summaryTh = "Commits, Branching, Merging, Pull Requests และ Workflow",
        summaryEn = "Snapshots, branching, diffing, pull requests, and collaborative workflows.",
        primaryLanguage = CodeLanguage.BASH,
        lessons = listOf(
            Lesson(
                id = "git_1",
                level = SkillLevel.BEGINNER,
                titleTh = "init, status, add และ commit",
                titleEn = "Core Git Snapshot Cycle",
                explanationTh = "Git เก็บประวัติโค้ดเป็น Snapshot ผ่านขั้นตอน Working Tree -> Staging -> Commit",
                explanationEn = "Track changes cleanly through working directory, staging area, and commits.",
                codeExample = "git init\ngit status\ngit add .\ngit commit -m \"feat: initial setup\"",
                language = CodeLanguage.BASH
            ),
            Lesson(
                id = "git_2",
                level = SkillLevel.INTERMEDIATE,
                titleTh = "Branching และ Pull Requests",
                titleEn = "Branches & Pull Requests",
                explanationTh = "สร้าง Branch แยกสำหรับแต่ละฟีเจอร์ (เช่น feature/login) เพื่อไม่ให้กระทบ Branch หลัก (main)",
                explanationEn = "Isolate new features in dedicated branches before opening Pull Requests into main.",
                codeExample = "git checkout -b feature/auth\ngit push -u origin feature/auth",
                language = CodeLanguage.BASH
            ),
            Lesson(
                id = "git_3",
                level = SkillLevel.ADVANCED,
                titleTh = "Rebase, Cherry-pick และ Release Tags",
                titleEn = "Rebase & Semantic Releases",
                explanationTh = "รักษาประวัติ Commit ให้สะอาดด้วย Interactive Rebase และติด Tag เวอร์ชัน v1.0.0",
                explanationEn = "Keep history linear with rebase and mark production releases with annotated tags.",
                codeExample = "git tag -a v1.0.0 -m \"Production release\"",
                language = CodeLanguage.BASH
            )
        ),
        challenges = listOf(
            CodingChallenge(
                id = "git_ch_1",
                titleTh = "ชุดคำสั่งสร้าง Branch และ Commit",
                titleEn = "Feature Branch Workflow",
                promptTh = "เขียนคำสั่ง git เพื่อสร้าง branch ชื่อ feat-ui, stage ไฟล์ และ commit",
                promptEn = "Write git commands to create branch feat-ui, stage files, and commit.",
                starterCode = "# Write your git workflow commands",
                expectedSubstring = "git commit",
                expectedKeywords = listOf("git", "feat-ui", "add", "commit"),
                language = CodeLanguage.BASH
            )
        ),
        quiz = listOf(
            QuizQuestion(
                id = "git_q1",
                questionTh = "คำสั่งใดใช้ตรวจสอบไฟล์ที่ถูกแก้ไขแต่ยังไม่ได้ Commit?",
                questionEn = "Which command shows modified and untracked files in the working tree?",
                optionsTh = listOf("git log", "git status", "git branch", "git clone"),
                optionsEn = listOf("git log", "git status", "git branch", "git clone"),
                correctIndex = 1,
                explanationTh = "git status แสดงสถานะไฟล์ที่เปลี่ยนแปลงและไฟล์ที่อยู่ใน Staging Area",
                explanationEn = "git status reports staged, modified, and untracked files."
            )
        ),
        finalProjectTitleTh = "คู่มือ Git Workflow ของทีม",
        finalProjectTitleEn = "Team Git Release Script",
        finalProjectBriefTh = "ทดลองใช้งาน Git จริงได้ในแท็บ Dev Tools > Git / GitHub และ Terminal",
        finalProjectBriefEn = "Practice real snapshot commits and diffs in Dev Tools > Git & Terminal.",
        finalProjectFiles = listOf(
            TemplateFileSpec(
                "release.sh",
                CodeLanguage.BASH,
                "#!/usr/bin/env bash\ngit status\necho \"Ready to tag release\""
            )
        )
    )
)
