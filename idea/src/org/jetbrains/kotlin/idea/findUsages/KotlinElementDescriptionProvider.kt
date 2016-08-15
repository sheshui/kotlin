/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.RefactoringDescriptionLocation
import com.intellij.usageView.UsageViewLongNameLocation
import com.intellij.usageView.UsageViewShortNameLocation
import com.intellij.usageView.UsageViewTypeLocation
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.refactoring.rename.RenameJavaSyntheticPropertyHandler
import org.jetbrains.kotlin.idea.refactoring.rename.RenameKotlinPropertyProcessor
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class KotlinElementDescriptionProvider : ElementDescriptionProvider {
    companion object {
        val REFACTORING_RENDERER = DescriptorRenderer.ONLY_NAMES_WITH_SHORT_TYPES.withOptions {
            withoutReturnType = true
            renderConstructorKeyword = false
        }
    }

    override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
        val shouldUnwrap = location !is UsageViewShortNameLocation && location !is UsageViewLongNameLocation
        val targetElement = if (shouldUnwrap) element.unwrapped ?: element else element

        fun elementKind() = when (targetElement) {
            is KtClass -> if (targetElement.isInterface()) "interface" else "class"
            is KtObjectDeclaration -> "object"
            is KtNamedFunction -> "function"
            is KtFunctionLiteral -> "lambda"
            is KtPrimaryConstructor, is KtSecondaryConstructor -> "constructor"
            is KtProperty -> if (targetElement.isLocal) "variable" else "property"
            is KtTypeParameter -> "type parameter"
            is KtParameter -> "parameter"
            is KtDestructuringDeclarationEntry -> "variable"
            is KtTypeAlias -> "type alias"
            is RenameJavaSyntheticPropertyHandler.SyntheticPropertyWrapper -> "property"
            is KtLightClassForFacade -> "facade class"
            is RenameKotlinPropertyProcessor.PropertyMethodWrapper -> "property accessor"
            else -> null
        }

        if (targetElement !is PsiNamedElement || targetElement.language != KotlinLanguage.INSTANCE) return null
        return when(location) {
            is UsageViewTypeLocation -> elementKind()
            is UsageViewShortNameLocation, is UsageViewLongNameLocation -> targetElement.name
            is RefactoringDescriptionLocation -> {
                val kind = elementKind() ?: return null
                val descriptor = (targetElement as KtDeclaration).descriptor ?: return null
                val renderFqName = location.includeParent() &&
                                   targetElement !is KtTypeParameter &&
                                   targetElement !is KtParameter &&
                                   targetElement !is KtConstructor<*>
                val desc = when (descriptor) {
                    is FunctionDescriptor -> {
                        val baseText = REFACTORING_RENDERER.render(descriptor)
                        val parentFqName = if (renderFqName) descriptor.containingDeclaration.fqNameSafe else null
                        if (parentFqName?.isRoot ?: true) baseText else "${parentFqName!!.asString()}.$baseText"
                    }
                    else -> if (renderFqName) DescriptorUtils.getFqName(descriptor).asString() else descriptor.name.asString()
                }

                "$kind ${CommonRefactoringUtil.htmlEmphasize(desc)}"
            }
            is HighlightUsagesDescriptionLocation -> {
                val kind = elementKind() ?: return null
                val descriptor = (targetElement as KtDeclaration).descriptor ?: return null
                "$kind ${descriptor.name.asString()}"
            }
            else -> null
        }
    }
}
