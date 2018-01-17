/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.scratch.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.keymap.KeymapUtil
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import org.jetbrains.kotlin.idea.scratch.ScratchFileLanguageProvider
import org.jetbrains.kotlin.idea.scratch.getScratchPanelFromSelectedEditor
import org.jetbrains.kotlin.idea.scratch.output.ProgressBarOutputHandler
import org.jetbrains.kotlin.idea.scratch.output.ScratchOutputHandlerAdapter
import org.jetbrains.kotlin.idea.scratch.ui.ScratchTopPanel

class RunScratchAction : AnAction(
    KotlinBundle.message("scratch.run.button"),
    KotlinBundle.message("scratch.run.button") + " (${KeymapUtil.getShortcutText(KeyboardShortcut.fromString(shortcut))})",
    AllIcons.Actions.Execute
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val scratchTopPanel = getScratchPanelFromSelectedEditor(project) ?: return
        doAction(scratchTopPanel)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val scratchTopPanel = getScratchPanelFromSelectedEditor(project) ?: return
        e.presentation.isEnabled = !scratchTopPanel.isCompilerRunning()
    }

    companion object {
        const val shortcut = "ctrl alt W"

        fun doAction(scratchTopPanel: ScratchTopPanel) {
            val scratchFile = scratchTopPanel.scratchFile

            val isMakeBeforeRun = scratchTopPanel.isMakeBeforeRun()
            val isRepl = scratchTopPanel.isRepl()

            val provider = ScratchFileLanguageProvider.get(scratchFile.psiFile.language) ?: return

            val handler = provider.getOutputHandler()

            val module = scratchTopPanel.getModule()
            if (module == null) {
                handler.error(scratchFile, "Module should be selected")
                handler.onFinish(scratchFile)
                return
            }

            val runnable = r@ {
                val executor = if (isRepl) provider.createReplExecutor(scratchFile) else provider.createCompilingExecutor(scratchFile)
                if (executor == null) {
                    handler.error(scratchFile, "Couldn't run ${scratchFile.psiFile.name}")
                    handler.onFinish(scratchFile)
                    return@r
                }

                scratchTopPanel.startCompilation()

                if (isRepl) {
                    executor.addOutputHandler(ProgressBarOutputHandler)
                }

                executor.addOutputHandler(handler)
                executor.addOutputHandler(object : ScratchOutputHandlerAdapter() {
                    override fun onFinish(file: ScratchFile) {
                        scratchTopPanel.stopCompilation()
                    }
                })

                executor.execute()
            }

            if (isMakeBeforeRun) {
                CompilerManager.getInstance(scratchFile.psiFile.project)
                    .make(module) { aborted, errors, _, _ -> if (!aborted && errors == 0) runnable() }
            } else {
                runnable()
            }
        }
    }
}
