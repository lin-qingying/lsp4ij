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
package com.redhat.devtools.lsp4ij.installation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Interface defining the contract for server (Language Server, DAP server) installation.
 */
public interface ServerInstaller {

    /**
     * Starts the server installation process.
     *
     * @return A {@link CompletableFuture} that completes when the installation is finished.
     */
    @NotNull
    CompletableFuture<ServerInstallationStatus> checkInstallation();

    /**
     * Starts the server installation process.
     *
     * @return A {@link CompletableFuture} that completes when the installation is finished.
     */
    @NotNull CompletableFuture<ServerInstallationStatus> checkInstallation(@NotNull ServerInstallationContext context);

    /**
     * Returns the current server installer status.
     *
     * @return the current server installer status.
     */
    @NotNull
    ServerInstallationStatus getStatus();

    /**
     * Code to be executed before installation (optional).
     *
     * @return A {@link Runnable} task to execute before installation, or {@code null} if no task is defined.
     */
    @Nullable
    default Runnable getBeforeCode(@NotNull ServerInstallationContext context) {
        return null;
    }

    /**
     * Code to be executed after installation (optional).
     *
     * @return A {@link Runnable} task to execute after installation, or {@code null} if no task is defined.
     */
    @Nullable
    default Runnable getAfterCode(@NotNull ServerInstallationContext context) {
        return null;
    }

    /**
     * Resets the installation state, allowing the process to be restarted if necessary.
     */
    void reset();

    void registerConsoleProvider(@NotNull ConsoleProvider consoleProvider);

    void unregisterConsoleProvider(@NotNull ConsoleProvider consoleProvider);
}
