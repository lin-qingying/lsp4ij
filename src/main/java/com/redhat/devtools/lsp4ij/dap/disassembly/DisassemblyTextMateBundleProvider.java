/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * Angelo Zerr - implementation of DAP disassembly editor support
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.disassembly;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

/**
 * TextMate bundle provider for Disassembly file.
 */
public class DisassemblyTextMateBundleProvider implements TextMateBundleProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(DisassemblyTextMateBundleProvider.class);

    private static final String RESOURCE_PATH = "/textmate";
    private static final String BUNDLE_NAME = "disasm";

    @NotNull
    @Override
    public List<PluginBundle> getBundles() {
        Path bundlePath = getBundlePath();
        PluginBundle pluginBundle = new PluginBundle(BUNDLE_NAME, bundlePath);
        return List.of(pluginBundle);
    }

    private Path getBundlePath() {
        try {
            IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId("com.redhat.devtools.lsp4ij"));
            String version = plugin.getVersion();
            String path = plugin.getPluginPath() + "/bundles/" + version;
            return copyResourceDirectory(path, List.of("package.json", "syntaxes/disassembly.json"));
        } catch (IOException ex) {
            LOGGER.error("Bundles error: " + ex);
            throw new UncheckedIOException(ex);
        }
    }

    private Path copyResourceDirectory(@NotNull String target, List<String> fileNames) throws IOException {
        Path targetPath = Paths.get(target);
        if (Files.notExists(targetPath)) {
            Files.createDirectories(targetPath);
        }
        for (String fileName: fileNames) {
            Path pathToCopyFile = new File(target + "/" + fileName).toPath();
            if (Files.notExists(pathToCopyFile)) {
                var dir = pathToCopyFile.getParent();
                if (Files.notExists(dir)) {
                    Files.createDirectories(dir);
                }
                Files.copy(
                        Objects.requireNonNull(DisassemblyTextMateBundleProvider.class.getResourceAsStream(RESOURCE_PATH + "/" + fileName)),
                        pathToCopyFile);
            }
        }
        return targetPath;
    }
}