/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.server.definition;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Language server definition listener API.
 */
public interface LanguageServerDefinitionListener {

    abstract class LanguageServerDefinitionEvent {

        public static enum UpdatedBy {
            USER,
            INSTALLER;
        }

        private final @NotNull LanguageServerDefinitionListener.LanguageServerDefinitionEvent.@NotNull UpdatedBy updatedBy;
        protected final Project project;

        LanguageServerDefinitionEvent(@NotNull UpdatedBy updatedBy,
                                      @NotNull Project project) {
            this.updatedBy = updatedBy;
            this.project = project;
        }

        public @NotNull UpdatedBy getUpdatedBy() {
            return updatedBy;
        }

        public @NotNull Project getProject() {
            return this.project;
        }
    }

    class LanguageServerAddedEvent extends LanguageServerDefinitionEvent {

        public final Collection<LanguageServerDefinition> serverDefinitions;

        public LanguageServerAddedEvent(@NotNull Project project,
                                        @NotNull Collection<LanguageServerDefinition> serverDefinitions) {
            super(UpdatedBy.USER, project);
            this.serverDefinitions = serverDefinitions;
        }
    }

    class LanguageServerRemovedEvent extends LanguageServerDefinitionEvent {

        public final Collection<LanguageServerDefinition> serverDefinitions;

        public LanguageServerRemovedEvent(@NotNull Project project,
                                          @NotNull Collection<LanguageServerDefinition> serverDefinitions) {
            super(UpdatedBy.USER, project);
            this.serverDefinitions = serverDefinitions;
        }
    }

    class LanguageServerChangedEvent extends LanguageServerDefinitionEvent {

        public final LanguageServerDefinition serverDefinition;

        public final boolean nameChanged;
        public final boolean commandChanged;
        public final boolean userEnvironmentVariablesChanged;
        public final boolean includeSystemEnvironmentVariablesChanged;
        public final boolean mappingsChanged;
        public final boolean clientConfigurationContentChanged;
        public final boolean installerConfigurationContentChanged;

        public LanguageServerChangedEvent(@NotNull UpdatedBy updatedBy,
                                          @NotNull Project project,
                                          @NotNull LanguageServerDefinition serverDefinition,
                                          boolean nameChanged,
                                          boolean commandChanged,
                                          boolean userEnvironmentVariablesChanged,
                                          boolean includeSystemEnvironmentVariablesChanged,
                                          boolean mappingsChanged,
                                          boolean clientConfigurationContentChanged,
                                          boolean installerConfigurationContentChanged) {
            super(updatedBy, project);
            this.serverDefinition = serverDefinition;
            this.nameChanged = nameChanged;
            this.commandChanged = commandChanged;
            this.userEnvironmentVariablesChanged = userEnvironmentVariablesChanged;
            this.includeSystemEnvironmentVariablesChanged = includeSystemEnvironmentVariablesChanged;
            this.mappingsChanged = mappingsChanged;
            this.clientConfigurationContentChanged = clientConfigurationContentChanged;
            this.installerConfigurationContentChanged = installerConfigurationContentChanged;
        }
    }

    void handleAdded(@NotNull LanguageServerDefinitionListener.LanguageServerAddedEvent event);

    void handleRemoved(@NotNull LanguageServerDefinitionListener.LanguageServerRemovedEvent event);

    void handleChanged(@NotNull LanguageServerDefinitionListener.LanguageServerChangedEvent event);
}
