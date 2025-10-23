/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.templates;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.dap.configurations.options.EnvironmentVariablesDataConfigurable;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.redhat.devtools.lsp4ij.templates.ServerTemplate.*;

/**
 * Abstract class for LSP/DAP server template JSON deserializer.
 * @param <T>
 */
public abstract class ServerTemplateJsonDeserializer<T extends ServerTemplate>  implements JsonDeserializer<T>  {

    private final @NotNull String baseUrl;

    public ServerTemplateJsonDeserializer(@NotNull String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public final T deserialize(JsonElement jsonElement,
                               Type type,
                               JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();

        // Create LSP/DAP server template
        T serverTemplate = createServerTemplateInstance();

        // Fill template with commons fields id, name, url
        fillServerIdentifier(jsonObject, serverTemplate);
        // Fill template with commons file mappings
        fillServerMappings(jsonObject, serverTemplate);
        // Fill template with custom Json data
        deserializeCustom(serverTemplate, jsonObject, type, jsonDeserializationContext);
        return serverTemplate;
    }

    private void fillServerIdentifier(@NotNull JsonObject jsonObject, @NotNull T serverTemplate) {
        var id = jsonObject.get(ID_JSON_PROPERTY);
        serverTemplate.setId(id != null ? id.getAsString() : null);
        var name = jsonObject.get(NAME_JSON_PROPERTY);
        serverTemplate.setName(name != null ? name.getAsString() : null);
        serverTemplate.setUrl(getUrl(jsonObject, serverTemplate.getId()));
        serverTemplate.setDev(JSONUtils.getBoolean(jsonObject, DEV_JSON_PROPERTY));
    }

    private void fillServerMappings(@NotNull JsonObject jsonObject, @NotNull T serverTemplate) {
        JsonArray fileTypeMappings = jsonObject.getAsJsonArray(FILE_TYPE_MAPPINGS_JSON_PROPERTY);
        if (fileTypeMappings != null && !fileTypeMappings.isEmpty()) {
            for (JsonElement ftm : fileTypeMappings) {
                String languageId = ftm.getAsJsonObject().get(LANGUAGE_ID_JSON_PROPERTY) != null ? ftm.getAsJsonObject().get(LANGUAGE_ID_JSON_PROPERTY).getAsString() : null;
                List<String> patterns = new ArrayList<>();
                JsonObject fileType = ftm.getAsJsonObject().getAsJsonObject(FILE_TYPE_JSON_PROPERTY);
                JsonArray patternArray = fileType.getAsJsonArray(PATTERNS_JSON_PROPERTY);
                if (patternArray != null) {
                    for (JsonElement pattern : patternArray) {
                        patterns.add(pattern.getAsString());
                    }
                }
                if (!patterns.isEmpty()) {
                    serverTemplate.addFileTypeMapping(ServerMappingSettings.createFileNamePatternsMappingSettings(patterns, languageId));
                }

                JsonElement language = fileType.get(NAME_JSON_PROPERTY);
                if (language != null) {
                    serverTemplate.addFileTypeMapping(ServerMappingSettings.createFileTypeMappingSettings(language.getAsString(), languageId));
                }
            }
        }
        JsonArray languageMappings = jsonObject.getAsJsonArray(LANGUAGE_MAPPINGS_JSON_PROPERTY);
        if (languageMappings != null && !languageMappings.isEmpty()) {
            for (JsonElement language : languageMappings) {
                String lang = language.getAsJsonObject().get(LANGUAGE_JSON_PROPERTY) != null ? language.getAsJsonObject().get(LANGUAGE_JSON_PROPERTY).getAsString() : null;
                String languageId = language.getAsJsonObject().get(LANGUAGE_ID_JSON_PROPERTY) != null ? language.getAsJsonObject().get(LANGUAGE_ID_JSON_PROPERTY).getAsString() : null;
                if (lang != null) {
                    serverTemplate.addLanguageMapping(ServerMappingSettings.createLanguageMappingSettings(lang, languageId));
                }
            }
        }
    }

    protected abstract void deserializeCustom(@NotNull T serverTemplate,
                                              @NotNull JsonObject jsonObject,
                                              Type type,
                                              JsonDeserializationContext jsonDeserializationContext) throws JsonParseException;

    private @Nullable String getUrl(@NotNull JsonObject jsonObject,
                                    @Nullable String id) {
        if (jsonObject.has(URL_JSON_PROPERTY)) {
            JsonElement url = jsonObject.get(URL_JSON_PROPERTY);
            if (url.isJsonPrimitive()) {
                if (url.getAsJsonPrimitive().isJsonNull()) {
                    return null;
                }
                if (url.getAsJsonPrimitive().isString()) {
                    return url.getAsJsonPrimitive().getAsString();
                }
            }
        }
        if (id != null && StringUtils.isNotBlank(id)) {
            return baseUrl + id + ".md";
        }
        return null;
    }

    protected @Nullable EnvironmentVariablesData deserializeEnvData(@NotNull JsonObject jsonObject) {
        JsonElement env = jsonObject.get(ENV_JSON_PROPERTY);
        if (env != null && env.isJsonObject()) {
            JsonObject envObject = env.getAsJsonObject();
            // includeSystemEnvironmentVariables
            boolean includeSystemEnvironmentVariables = false;
            JsonElement includeSystem = envObject.get(ENV_INCLUDE_SYSTEM_JSON_PROPERTY);
            if (includeSystem != null && includeSystem.isJsonPrimitive() && includeSystem.getAsJsonPrimitive().isBoolean()) {
                includeSystemEnvironmentVariables = includeSystem.getAsJsonPrimitive().getAsBoolean();
            }
            // variables
            Map<String, String> envs = Collections.emptyMap();
            JsonElement variables = envObject.get(ENV_VARIABLES_JSON_PROPERTY);
            if (variables != null && variables.isJsonObject()) {
                Gson gson = new Gson();
                Type mapType = new TypeToken<Map<String, String>>() {
                }.getType();
                envs = gson.fromJson(variables, mapType);
            }
            return EnvironmentVariablesData.create(envs , includeSystemEnvironmentVariables);
        }
        return null;
    }

    protected abstract T createServerTemplateInstance();
}
