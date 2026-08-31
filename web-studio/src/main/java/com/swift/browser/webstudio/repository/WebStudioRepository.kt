package com.swift.browser.webstudio.repository

import android.content.Context
import android.net.Uri
import com.swift.browser.webstudio.model.ProjectModel
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WebStudioRepository(private val context: Context) {

    fun createProjectDirectory(name: String): File {
        val projectsDir = File(context.filesDir, "webstudio_projects")
        if (!projectsDir.exists()) {
            projectsDir.mkdirs()
        }
        val safeName = name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val projectDir = File(projectsDir, "${safeName}_${System.currentTimeMillis()}")
        projectDir.mkdirs()
        return projectDir
    }

    fun listProjects(): List<ProjectModel> {
        val projectsDir = File(context.filesDir, "webstudio_projects")
        if (!projectsDir.exists() || !projectsDir.isDirectory) return emptyList()
        return projectsDir.listFiles()?.filter { it.isDirectory }?.map { dir ->
            ProjectModel(
                name = dir.name.substringBeforeLast("_"),
                rootDir = dir,
                lastModifiedAt = dir.lastModified()
            )
        }?.sortedByDescending { it.lastModifiedAt } ?: emptyList()
    }

    fun deleteProject(dir: File): Boolean {
        return try {
            dir.deleteRecursively()
        } catch (e: Exception) {
            false
        }
    }

    fun createFile(parentDir: File, name: String, content: String): File? {
        return try {
            val target = File(parentDir, name)
            if (target.exists()) return null
            target.writeText(content)
            target
        } catch (e: Exception) {
            null
        }
    }

    fun createFolder(parentDir: File, name: String): File? {
        return try {
            val target = File(parentDir, name)
            if (target.exists()) return null
            target.mkdirs()
            target
        } catch (e: Exception) {
            null
        }
    }

    fun deleteFile(file: File): Boolean {
        return try {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } catch (e: Exception) {
            false
        }
    }

    fun renameFile(file: File, newName: String): Boolean {
        return try {
            val newFile = File(file.parentFile, newName)
            file.renameTo(newFile)
        } catch (e: Exception) {
            false
        }
    }

    fun zipProject(projectDir: File, outputFile: File): Boolean {
        return try {
            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                projectDir.walkTopDown().forEach { file ->
                    val relativePath = file.relativeTo(projectDir).path
                    if (file.isDirectory) {
                        if (relativePath.isNotEmpty()) {
                            zos.putNextEntry(ZipEntry("$relativePath/"))
                            zos.closeEntry()
                        }
                    } else {
                        zos.putNextEntry(ZipEntry(relativePath))
                        file.inputStream().use { input -> input.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
