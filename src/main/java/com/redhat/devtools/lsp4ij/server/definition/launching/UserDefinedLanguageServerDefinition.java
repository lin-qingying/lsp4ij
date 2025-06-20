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
package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerManager;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.Map;

import static com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils.resolveCommandLine;

/**
 * {@link com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition} implementation to start a
 * language server with a process command defined by the user.
 */
public class UserDefinedLanguageServerDefinition extends LanguageServerDefinition implements ClientConfigurableLanguageServerDefinition, CommandLineUpdater {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserDefinedLanguageServerDefinition.class);//$NON-NLS-1$

    @SerializedName("displayName")
    private String name;
    private String templateId;
    private String commandLine;
    private Map<String, String> userEnvironmentVariables;
    private boolean includeSystemEnvironmentVariables;
    private String configurationContent;
    private Object configuration;
    private String configurationSchemaContent;
    private String initializationOptionsContent;
    private Object initializationOptions;
    private String clientConfigurationContent;
    private ClientConfigurationSettings clientConfiguration;
    private @Nullable ServerInstallerDescriptor serverInstallerDescriptor;
    private String installerConfigurationContent;

    public UserDefinedLanguageServerDefinition(@NotNull String id,
                                               @Nullable String templateId,
                                               @NotNull String name,
                                               @Nullable String description,
                                               @NotNull String commandLine,
                                               @NotNull Map<String, String> userEnvironmentVariables,
                                               boolean includeSystemEnvironmentVariables,
                                               @Nullable String configurationContent,
                                               @Nullable String configurationSchemaContent,
                                               @Nullable String initializationOptionsContent,
                                               @Nullable String clientConfigurationContent,
                                               @Nullable String installerConfigurationContent) {
        super(id, name, description, true, null, false);
        this.name = name;
        this.templateId = templateId;
        this.commandLine = commandLine;
        this.userEnvironmentVariables = userEnvironmentVariables;
        this.includeSystemEnvironmentVariables = includeSystemEnvironmentVariables;
        this.configurationContent = configurationContent;
        this.configurationSchemaContent = configurationSchemaContent;
        this.initializationOptionsContent = initializationOptionsContent;
        this.clientConfigurationContent = clientConfigurationContent;
        this.installerConfigurationContent = installerConfigurationContent;
    }

    // Backward-compatible signature for clients calling without client configuration content
    public UserDefinedLanguageServerDefinition(@NotNull String id,
                                               @NotNull String name,
                                               @Nullable String description,
                                               @NotNull String commandLine,
                                               @NotNull Map<String, String> userEnvironmentVariables,
                                               boolean includeSystemEnvironmentVariables,
                                               @Nullable String configurationContent,
                                               @Nullable String initializationOptionsContent) {
        this(id,
                null,
                name,
                description,
                commandLine,
                userEnvironmentVariables,
                includeSystemEnvironmentVariables,
                configurationContent,
                null,
                initializationOptionsContent,
                null,
                null);
    }

    @Override
    public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
        String resolvedCommandLine = resolveCommandLine(commandLine, project);
        return new UserDefinedStreamConnectionProvider(resolvedCommandLine,
                userEnvironmentVariables,
                includeSystemEnvironmentVariables,
                this,
                project);
    }

    @Override
    public @NotNull LanguageClientImpl createLanguageClient(@NotNull Project project) {
        return new UserDefinedLanguageClient(this, project);
    }

    @Override
    public @NotNull LSPClientFeatures createClientFeatures() {
        return new UserDefinedClientFeatures();
    }

    /**
     * Returns the template id which has been used to create the language server definition and null otherwise.
     *
     * @return the template id which has been used to create the language server definition and null otherwise.
     */
    @Nullable
    public String getTemplateId() {
        return templateId;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getCommandLine() {
        return commandLine;
    }

    @Override
    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    public Map<String, String> getUserEnvironmentVariables() {
        return userEnvironmentVariables;
    }

    public void setUserEnvironmentVariables(Map<String, String> userEnvironmentVariables) {
        this.userEnvironmentVariables = userEnvironmentVariables;
    }

    public boolean isIncludeSystemEnvironmentVariables() {
        return includeSystemEnvironmentVariables;
    }

    public void setIncludeSystemEnvironmentVariables(boolean includeSystemEnvironmentVariables) {
        this.includeSystemEnvironmentVariables = includeSystemEnvironmentVariables;
    }

    public String getConfigurationContent() {
        return configurationContent;
    }

    public void setConfigurationContent(String configurationContent) {
        this.configurationContent = configurationContent;
        this.configuration = null;
    }

    public String getConfigurationSchemaContent() {
        return configurationSchemaContent;
    }

    public void setConfigurationSchemaContent(String configurationSchemaContent) {
        this.configurationSchemaContent = configurationSchemaContent;
    }

    public String getInitializationOptionsContent() {
        return initializationOptionsContent;
    }

    public void setInitializationOptionsContent(String initializationOptionsContent) {
        this.initializationOptionsContent = initializationOptionsContent;
        this.initializationOptions = null;
    }

    public String getClientConfigurationContent() {
        return clientConfigurationContent;
    }

    public void setClientConfigurationContent(String clientConfigurationContent) {
        this.clientConfigurationContent = clientConfigurationContent;
        this.clientConfiguration = null;
    }

    public String getInstallerConfigurationContent() {
        return installerConfigurationContent;
    }

    public void setInstallerConfigurationContent(String installerConfigurationContent) {
        this.installerConfigurationContent = installerConfigurationContent;
        this.serverInstallerDescriptor = null;
    }

    public Object getLanguageServerConfiguration() {
        if (configuration == null && configurationContent != null && !configurationContent.isBlank()) {
            try {
                configuration = JsonParser.parseReader(new StringReader(configurationContent));
            } catch (Exception e) {
                LOGGER.error("Error while parsing JSON configuration for the language server '" + getId() + "'", e);
            }
        }
        return configuration;
    }

    public Object getLanguageServerInitializationOptions() {
        if (initializationOptions == null && initializationOptionsContent != null && !initializationOptionsContent.isEmpty()) {
            try {
                initializationOptions = JsonParser.parseReader(new StringReader(initializationOptionsContent));
            } catch (Exception e) {
                LOGGER.error("Error while parsing JSON Initialization Options for the language server '" + getId() + "'", e);
            }
        }
        return initializationOptions;
    }

    @Override
    @Nullable
    public ClientConfigurationSettings getLanguageServerClientConfiguration() {
        if ((clientConfiguration == null) && (clientConfigurationContent != null) && !clientConfigurationContent.isBlank()) {
            try {
                clientConfiguration = JSONUtils.getLsp4jGson().fromJson(clientConfigurationContent, ClientConfigurationSettings.class);
            } catch (Exception e) {
                LOGGER.error("Error while parsing JSON client configuration for the language server '" + getId() + "'", e);
            }
        }
        return clientConfiguration;
    }

    @Nullable
    public ServerInstallerDescriptor getServerInstallerDescriptor() {
        if ((serverInstallerDescriptor == null) && (installerConfigurationContent != null) && !installerConfigurationContent.isBlank()) {
            try {
                serverInstallerDescriptor = ServerInstallerManager.getInstance().loadInstaller(installerConfigurationContent);
            } catch (Exception e) {
                // Do nothing
            }
        }
        return serverInstallerDescriptor;
    }
    
    @Override
    public @NotNull String getDisplayName() {
        return name;
    }
}
