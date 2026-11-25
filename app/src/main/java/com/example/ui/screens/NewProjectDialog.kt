package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.CodeLanguage
import com.example.data.model.ProjectTemplate
import com.example.data.model.ProjectTemplateCatalog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewProjectDialog(
    isThai: Boolean,
    onDismiss: () -> Unit,
    onCreateFromTemplate: (name: String, template: ProjectTemplate) -> Unit,
    onCreateCustom: (name: String, description: String, language: CodeLanguage) -> Unit
) {
    var useCustom by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf(ProjectTemplateCatalog.templates.first()) }
    var selectedLanguage by remember { mutableStateOf(CodeLanguage.PYTHON) }
    var projectName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isThai) "สร้างโปรเจกต์ใหม่" else "Create New Project",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !useCustom,
                        onClick = { useCustom = false },
                        label = { Text(if (isThai) "จากเทมเพลต" else "From Template") }
                    )
                    FilterChip(
                        selected = useCustom,
                        onClick = { useCustom = true },
                        label = { Text(if (isThai) "เลือกภาษาเอง (16 ภาษา)" else "Custom Language (16)") }
                    )
                }

                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text(if (isThai) "ชื่อโปรเจกต์" else "Project Name") },
                    placeholder = {
                        Text(
                            if (useCustom) "my-${selectedLanguage.id}-app"
                            else if (isThai) selectedTemplate.titleTh else selectedTemplate.titleEn
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_project_name_input")
                )

                if (!useCustom) {
                    Text(
                        if (isThai) "เลือกเทมเพลตโปรเจกต์:" else "Choose Project Template:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProjectTemplateCatalog.templates.forEach { tpl ->
                            FilterChip(
                                selected = tpl.id == selectedTemplate.id,
                                onClick = { selectedTemplate = tpl },
                                label = { Text(if (isThai) tpl.titleTh else tpl.titleEn) }
                            )
                        }
                    }
                    Text(
                        text = if (isThai) selectedTemplate.descriptionTh else selectedTemplate.descriptionEn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(if (isThai) "คำอธิบาย (ไม่บังคับ)" else "Description (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (isThai) "เลือกภาษาหลัก:" else "Primary Language:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CodeLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = lang == selectedLanguage,
                                onClick = { selectedLanguage = lang },
                                label = { Text(lang.displayName) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!useCustom) {
                        val finalName = projectName.trim().ifBlank {
                            if (isThai) selectedTemplate.titleTh else selectedTemplate.titleEn
                        }
                        onCreateFromTemplate(finalName, selectedTemplate)
                    } else {
                        val finalName = projectName.trim().ifBlank { "My ${selectedLanguage.displayName} Project" }
                        onCreateCustom(finalName, description.trim(), selectedLanguage)
                    }
                },
                modifier = Modifier.testTag("confirm_create_project_btn")
            ) {
                Text(if (isThai) "สร้างโปรเจกต์" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isThai) "ยกเลิก" else "Cancel")
            }
        }
    )
}
