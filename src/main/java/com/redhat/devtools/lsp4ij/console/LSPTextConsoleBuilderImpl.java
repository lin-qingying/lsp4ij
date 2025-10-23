/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.console;

import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * Extends {@link TextConsoleBuilderImpl} to create the custom {@link LSPConsoleView}.
 */
public class LSPTextConsoleBuilderImpl extends TextConsoleBuilderImpl {

    private final LanguageServerDefinition serverDefinition;
    private final boolean withFolding;

    public LSPTextConsoleBuilderImpl(boolean withFolding,
                                     @NotNull LanguageServerDefinition serverDefinition,
                                     @NotNull Project project) {
        super(project);
        this.withFolding = withFolding;
        this.serverDefinition = serverDefinition;
    }

    @Override
    protected @NotNull ConsoleView createConsole() {
        if (!withFolding) {
            return new LSPConsoleViewBase(getProject(), getScope(), isViewer(), isUsePredefinedMessageFilter());
        }
        return new LSPConsoleView(serverDefinition, getProject(), getScope(), isViewer(), isUsePredefinedMessageFilter());
    }
}
