package com.example.data.model

data class TemplateFileSpec(
    val path: String,
    val language: CodeLanguage,
    val content: String
)

data class ProjectTemplate(
    val id: String,
    val titleTh: String,
    val titleEn: String,
    val descriptionTh: String,
    val descriptionEn: String,
    val category: String,
    val primaryLanguage: CodeLanguage,
    val files: List<TemplateFileSpec>
)

object ProjectTemplateCatalog {
    val templates: List<ProjectTemplate> = listOf(
        ProjectTemplate(
            id = "website",
            titleTh = "เว็บไซต์โปรไฟล์สมัยใหม่",
            titleEn = "Modern Portfolio Website",
            descriptionTh = "HTML5 + CSS3 + JavaScript พร้อมพรีวิวสดในแอป",
            descriptionEn = "Responsive HTML5 + CSS3 + JS with live preview",
            category = "Website",
            primaryLanguage = CodeLanguage.HTML,
            files = listOf(
                TemplateFileSpec(
                    path = "index.html",
                    language = CodeLanguage.HTML,
                    content = """
                        <!DOCTYPE html>
                        <html lang="th">
                        <head>
                          <meta charset="UTF-8" />
                          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                          <title>PoraStudio Portfolio</title>
                          <link rel="stylesheet" href="styles.css" />
                        </head>
                        <body>
                          <main class="card">
                            <span class="badge">Available for Work</span>
                            <h1>สวัสดี, ฉันคือนักพัฒนาซอฟต์แวร์</h1>
                            <p>สร้างด้วย PoraStudio Mobile IDE</p>
                            <button id="greetBtn">ทักทาย (Click Me)</button>
                            <p id="status"></p>
                          </main>
                          <script src="app.js"></script>
                        </body>
                        </html>
                    """.trimIndent()
                ),
                TemplateFileSpec(
                    path = "styles.css",
                    language = CodeLanguage.CSS,
                    content = """
                        * { box-sizing: border-box; margin: 0; padding: 0; }
                        body {
                          font-family: system-ui, sans-serif;
                          background: linear-gradient(135deg, #0b1020, #1e293b);
                          color: #f1f5f9;
                          min-height: 100vh;
                          display: grid;
                          place-items: center;
                          padding: 20px;
                        }
                        .card {
                          background: rgba(17, 24, 39, 0.9);
                          border: 1px solid #38bdf8;
                          border-radius: 18px;
                          padding: 28px;
                          max-width: 420px;
                          box-shadow: 0 20px 40px rgba(0,0,0,0.4);
                        }
                        .badge {
                          display: inline-block;
                          background: #0c354d;
                          color: #38bdf8;
                          padding: 4px 12px;
                          border-radius: 999px;
                          font-size: 12px;
                          margin-bottom: 12px;
                        }
                        h1 { font-size: 22px; margin-bottom: 8px; }
                        p { color: #94a3b8; margin-bottom: 16px; }
                        button {
                          background: #38bdf8;
                          color: #041e2b;
                          border: none;
                          padding: 10px 18px;
                          border-radius: 10px;
                          font-weight: 700;
                          cursor: pointer;
                        }
                    """.trimIndent()
                ),
                TemplateFileSpec(
                    path = "app.js",
                    language = CodeLanguage.JAVASCRIPT,
                    content = """
                        let clicks = 0;
                        const btn = document.getElementById("greetBtn");
                        const status = document.getElementById("status");
                        btn.addEventListener("click", () => {
                          clicks++;
                          status.textContent = "ขอบคุณที่ทักทาย! กดแล้ว " + clicks + " ครั้ง";
                          console.log("Portfolio interaction count:", clicks);
                        });
                    """.trimIndent()
                ),
                TemplateFileSpec(
                    path = "README.md",
                    language = CodeLanguage.HTML,
                    content = "# Modern Portfolio Website\n\nBuilt with HTML5, CSS3, and Vanilla JavaScript.\nRun `index.html` to preview live!"
                )
            )
        ),
        ProjectTemplate(
            id = "web_app",
            titleTh = "เว็บแอป Task & Expense Tracker",
            titleEn = "Interactive Web Application",
            descriptionTh = "เว็บแอปจัดการงานแบบโต้ตอบด้วย JS State + CSS Grid",
            descriptionEn = "Interactive JS state web app with live DOM rendering",
            category = "Web App",
            primaryLanguage = CodeLanguage.HTML,
            files = listOf(
                TemplateFileSpec(
                    path = "index.html",
                    language = CodeLanguage.HTML,
                    content = """
                        <!DOCTYPE html>
                        <html lang="th">
                        <head>
                          <meta charset="UTF-8" />
                          <title>TaskFlow Web App</title>
                          <link rel="stylesheet" href="app.css" />
                        </head>
                        <body>
                          <div class="container">
                            <h2>TaskFlow Studio</h2>
                            <div class="row">
                              <input id="taskInput" placeholder="เพิ่มงานใหม่..." />
                              <button onclick="addTask()">เพิ่ม</button>
                            </div>
                            <ul id="taskList"></ul>
                          </div>
                          <script src="main.js"></script>
                        </body>
                        </html>
                    """.trimIndent()
                ),
                TemplateFileSpec(
                    path = "app.css",
                    language = CodeLanguage.CSS,
                    content = """
                        body { background:#0b1020; color:#f1f5f9; font-family:sans-serif; padding:20px; }
                        .container { max-width:480px; margin:auto; background:#111827; padding:20px; border-radius:16px; border:1px solid #283548; }
                        .row { display:flex; gap:8px; margin:14px 0; }
                        input { flex:1; padding:10px; border-radius:10px; border:1px solid #334155; background:#1e293b; color:#fff; }
                        button { background:#34d399; color:#04261a; border:none; padding:10px 16px; border-radius:10px; font-weight:700; }
                        li { list-style:none; padding:10px; background:#1e293b; border-radius:10px; margin-bottom:8px; display:flex; justify-content:space-between; }
                    """.trimIndent()
                ),
                TemplateFileSpec(
                    path = "main.js",
                    language = CodeLanguage.JAVASCRIPT,
                    content = """
                        const tasks = ["ออกแบบ Architecture", "เขียน Unit Test"];
                        function render() {
                          const list = document.getElementById("taskList");
                          list.innerHTML = tasks.map((t, i) => '<li><span>' + t + '</span><button onclick="removeTask(' + i + ')">ลบ</button></li>').join("");
                        }
                        function addTask() {
                          const input = document.getElementById("taskInput");
                          if (!input.value.trim()) return;
                          tasks.push(input.value.trim());
                          console.log("Added task:", input.value);
                          input.value = "";
                          render();
                        }
                        function removeTask(idx) {
                          tasks.splice(idx, 1);
                          render();
                        }
                        render();
                    """.trimIndent()
                )
            )
        ),
        ProjectTemplate(
            id = "database_app",
            titleTh = "ฐานข้อมูล SQLite & รายงาน",
            titleEn = "SQLite Database App",
            descriptionTh = "รันคำสั่ง SQL จริงบนเอนจิน SQLite ในเครื่อง",
            descriptionEn = "Real local SQLite schema, seed data & analytics queries",
            category = "Database App",
            primaryLanguage = CodeLanguage.SQL,
            files = listOf(
                TemplateFileSpec(
                    path = "schema.sql",
                    language = CodeLanguage.SQL,
                    content = """
                        CREATE TABLE IF NOT EXISTS products (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          sku TEXT UNIQUE NOT NULL,
                          name TEXT NOT NULL,
                          price REAL NOT NULL,
                          stock INTEGER NOT NULL DEFAULT 0
                        );

                        INSERT OR IGNORE INTO products (sku, name, price, stock) VALUES
                          ('CV-001', 'Mechanical Keyboard', 2490.0, 14),
                          ('CV-002', 'USB-C Hub 8-in-1', 1290.0, 28),
                          ('CV-003', '4K IPS Monitor', 11900.0, 5);

                        SELECT sku, name, price, stock, (price * stock) AS inventory_value
                        FROM products
                        ORDER BY inventory_value DESC;
                    """.trimIndent()
                ),
                TemplateFileSpec(
                    path = "queries.sql",
                    language = CodeLanguage.SQL,
                    content = """
                        SELECT COUNT(*) AS total_skus, SUM(stock) AS total_units, SUM(price * stock) AS total_value
                        FROM products;
                    """.trimIndent()
                )
            )
        ),
        ProjectTemplate(
            id = "game",
            titleTh = "เกมอาร์เคด HTML5 Canvas",
            titleEn = "HTML5 Canvas Space Dodger",
            descriptionTh = "เกมโต้ตอบ 60fps บน Canvas พร้อมคะแนนสูงสุด",
            descriptionEn = "Interactive 60fps Canvas arcade game with touch controls",
            category = "Game",
            primaryLanguage = CodeLanguage.HTML,
            files = listOf(
                TemplateFileSpec(
                    path = "index.html",
                    language = CodeLanguage.HTML,
                    content = """
                        <!DOCTYPE html>
                        <html lang="th">
                        <head>
                          <meta charset="UTF-8" />
                          <title>Space Dodger</title>
                          <style>
                            body { margin:0; background:#0b1020; color:#f1f5f9; font-family:sans-serif; display:flex; flex-direction:column; align-items:center; justify-content:center; height:100vh; }
                            canvas { border:2px solid #38bdf8; border-radius:14px; background:#050814; touch-action:none; }
                            .hud { margin-bottom:10px; font-weight:700; color:#38bdf8; }
                          </style>
                        </head>
                        <body>
                          <div class="hud">Score: <span id="score">0</span> (แตะหรือลากเพื่อบังคับยาน)</div>
                          <canvas id="game" width="320" height="420"></canvas>
                          <script src="game.js"></script>
                        </body>
                        </html>
                    """.trimIndent()
                ),
                TemplateFileSpec(
                    path = "game.js",
                    language = CodeLanguage.JAVASCRIPT,
                    content = """
                        const canvas = document.getElementById("game");
                        const ctx = canvas.getContext("2d");
                        const scoreEl = document.getElementById("score");
                        let playerX = 160, score = 0, alive = true;
                        const meteors = [{ x: 80, y: 0, speed: 3 }, { x: 220, y: -180, speed: 3.6 }];

                        canvas.addEventListener("pointermove", (e) => {
                          const rect = canvas.getBoundingClientRect();
                          playerX = Math.max(20, Math.min(300, e.clientX - rect.left));
                        });

                        function loop() {
                          ctx.clearRect(0, 0, 320, 420);
                          ctx.fillStyle = "#38bdf8";
                          ctx.beginPath();
                          ctx.arc(playerX, 380, 14, 0, Math.PI * 2);
                          ctx.fill();

                          ctx.fillStyle = "#f43f5e";
                          for (const m of meteors) {
                            m.y += m.speed;
                            if (m.y > 430) { m.y = -20; m.x = 20 + Math.random() * 280; score++; scoreEl.textContent = score; }
                            ctx.beginPath();
                            ctx.arc(m.x, m.y, 12, 0, Math.PI * 2);
                            ctx.fill();
                            if (Math.hypot(m.x - playerX, m.y - 380) < 24) alive = false;
                          }
                          if (alive) requestAnimationFrame(loop);
                          else {
                            ctx.fillStyle = "#fff";
                            ctx.font = "bold 20px sans-serif";
                            ctx.fillText("GAME OVER - Score: " + score, 42, 210);
                          }
                        }
                        loop();
                    """.trimIndent()
                )
            )
        ),
        ProjectTemplate(
            id = "rest_api",
            titleTh = "REST API Service (TypeScript)",
            titleEn = "REST API Service (TypeScript)",
            descriptionTh = "โครงสร้าง Express/TypeScript API + package.json",
            descriptionEn = "Structured TypeScript REST API & package configuration",
            category = "REST API",
            primaryLanguage = CodeLanguage.TYPESCRIPT,
            files = listOf(
                TemplateFileSpec(
                    path = "src/server.ts",
                    language = CodeLanguage.TYPESCRIPT,
                    content = """
                        interface ApiTask {
                          id: string;
                          title: string;
                          completed: boolean;
                        }

                        const tasks: ApiTask[] = [
                          { id: "1", title: "Deploy REST API", completed: true },
                          { id: "2", title: "Add rate limiting", completed: false }
                        ];

                        export function listTasks(): ApiTask[] {
                          return tasks;
                        }

                        console.log("API Ready:", JSON.stringify(listTasks()));
                    """.trimIndent()
                ),
                TemplateFileSpec(
                    path = "package.json",
                    language = CodeLanguage.JSON,
                    content = """
                        {
                          "name": "porastudio-rest-api",
                          "version": "1.0.0",
                          "scripts": {
                            "dev": "tsx src/server.ts",
                            "build": "tsc"
                          },
                          "dependencies": {
                            "express": "^4.19.2"
                          },
                          "devDependencies": {
                            "typescript": "^5.5.0"
                          }
                        }
                    """.trimIndent()
                )
            )
        ),
        ProjectTemplate(
            id = "python_tool",
            titleTh = "เครื่องมือวิเคราะห์ข้อมูล Python",
            titleEn = "Python CLI & Automation Tool",
            descriptionTh = "สคริปต์ Python + requirements.txt (ต้องใช้ Sandbox Server เพื่อรัน)",
            descriptionEn = "Python CLI data tool + requirements.txt (uses sandbox runner)",
            category = "Python Tool",
            primaryLanguage = CodeLanguage.PYTHON,
            files = listOf(
                TemplateFileSpec(
                    path = "main.py",
                    language = CodeLanguage.PYTHON,
                    content = """
                        from dataclasses import dataclass

                        @dataclass
                        class Metric:
                            name: str
                            value: float

                        def summarize(metrics: list[Metric]) -> dict[str, float]:
                            total = sum(m.value for m in metrics)
                            avg = total / len(metrics) if metrics else 0.0
                            return {"total": total, "average": round(avg, 2)}

                        if __name__ == "__main__":
                            sample = [Metric("latency_ms", 42.5), Metric("latency_ms", 38.0)]
                            print("Summary:", summarize(sample))
                    """.trimIndent()
                ),
                TemplateFileSpec(
                    path = "requirements.txt",
                    language = CodeLanguage.BASH,
                    content = "httpx>=0.27.0\npydantic>=2.7.0\npytest>=8.0.0"
                )
            )
        ),
        ProjectTemplate(
            id = "android_app",
            titleTh = "แอป Android Jetpack Compose",
            titleEn = "Android Jetpack Compose App",
            descriptionTh = "โค้ด Kotlin + Compose + AndroidManifest + Gradle",
            descriptionEn = "Kotlin + Jetpack Compose starter module",
            category = "Android App",
            primaryLanguage = CodeLanguage.KOTLIN,
            files = listOf(
                TemplateFileSpec(
                    path = "MainActivity.kt",
                    language = CodeLanguage.KOTLIN,
                    content = """
                        package com.example.myapp

                        data class Note(val id: Int, val title: String)

                        fun main() {
                            val notes = listOf(Note(1, "Compose State"), Note(2, "Material 3"))
                            notes.forEach { println("${'$'}{it.id}: ${'$'}{it.title}") }
                        }
                    """.trimIndent()
                ),
                TemplateFileSpec(
                    path = "AndroidManifest.xml",
                    language = CodeLanguage.XML,
                    content = """
                        <?xml version="1.0" encoding="utf-8"?>
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                            <application android:label="MyComposeApp" />
                        </manifest>
                    """.trimIndent()
                )
            )
        ),
        ProjectTemplate(
            id = "backend_service",
            titleTh = "Backend Service (Java / Spring)",
            titleEn = "Backend Service (Java)",
            descriptionTh = "บริการ Backend ด้วย Java พร้อม Model และ Controller",
            descriptionEn = "Structured Java backend service skeleton",
            category = "Backend Service",
            primaryLanguage = CodeLanguage.JAVA,
            files = listOf(
                TemplateFileSpec(
                    path = "src/Main.java",
                    language = CodeLanguage.JAVA,
                    content = """
                        import java.util.List;

                        public class Main {
                            record ServiceHealth(String status, int activeWorkers) {}

                            public static void main(String[] args) {
                                var health = new ServiceHealth("UP", 4);
                                System.out.println("Service: " + health.status() + " workers=" + health.activeWorkers());
                            }
                        }
                    """.trimIndent()
                )
            )
        ),
        ProjectTemplate(
            id = "automation_script",
            titleTh = "สคริปต์ Automation (Bash)",
            titleEn = "DevOps Automation Script (Bash)",
            descriptionTh = "สคริปต์ตรวจสอบระบบและสร้างบิลด์อัตโนมัติ",
            descriptionEn = "CI/CD build & health automation shell script",
            category = "Automation Script",
            primaryLanguage = CodeLanguage.BASH,
            files = listOf(
                TemplateFileSpec(
                    path = "deploy.sh",
                    language = CodeLanguage.BASH,
                    content = """
                        #!/usr/bin/env bash
                        set -euo pipefail

                        APP_ENV="${'$'}{1:-staging}"
                        echo "Running preflight checks for ${'$'}APP_ENV..."
                        echo "Build verification passed."
                    """.trimIndent()
                )
            )
        )
    )
}
