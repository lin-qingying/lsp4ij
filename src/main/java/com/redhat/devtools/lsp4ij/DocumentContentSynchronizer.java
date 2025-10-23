/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.Alarm;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Synchronize IntelliJ document (open, content changed, close, save)
 * with LSP notifications (didOpen, didChanged, didClose, didSave).
 */
public class DocumentContentSynchronizer implements DocumentListener, Disposable {

    public enum RefreshPullDiagnosticOrigin {
        ON_REGISTER_CAPABILITY,
        ON_WORKSPACE_REFRESH
    }

    private static final long WAIT_AFTER_SENDING_DID_OPEN = 500L;

    private final @NotNull LanguageServerWrapper languageServerWrapper;
    private final @NotNull Document document;
    private final @NotNull String fileUri;
    private final @NotNull TextDocumentSyncOptions syncOptions;
    private final @NotNull VirtualFile file;
    private final @Nullable String documentText;
    private final @Nullable String languageId;

    private int version = 0;
    private final List<TextDocumentContentChangeEvent> changeEvents;
    private @Nullable CompletableFuture<LanguageServer> didOpenFuture;

    private volatile Alarm debouncePullDiagnosticsAlarm = null;
    private boolean diagnosticNotPulledOnDidOpen;

    public DocumentContentSynchronizer(@NotNull LanguageServerWrapper languageServerWrapper,
                                       @NotNull String fileUri,
                                       @NotNull VirtualFile file,
                                       @NotNull Document document,
                                       @Nullable String documentText,
                                       @Nullable String languageId,
                                       @NotNull TextDocumentSyncOptions syncOptions) {
        this.languageServerWrapper = languageServerWrapper;
        this.file = file;
        this.fileUri = fileUri;
        this.syncOptions = syncOptions;
        this.document = document;
        this.documentText = documentText;
        this.languageId = languageId;

        // Initialize LSP change events
        changeEvents = new ArrayList<>();
    }

    public @NotNull CompletableFuture<LanguageServer> getDidOpenFuture() {
        if (didOpenFuture != null) {
            return didOpenFuture;
        }
        return getDidOpenFutureSync();
    }

    private synchronized @NotNull CompletableFuture<LanguageServer> getDidOpenFutureSync() {
        if (didOpenFuture != null) {
            return didOpenFuture;
        }

        if (!syncOptions.getOpenClose()) {
            // Don't send textDocument/didOpen
            return didOpenFuture = languageServerWrapper
                    .getInitializedServer();
        }

        // add a document buffer
        TextDocumentItem textDocument = new TextDocumentItem();
        textDocument.setUri(this.fileUri);
        textDocument.setText(documentText != null ? documentText : document.getText());

        @NotNull String languageId = this.languageId != null ? this.languageId :
                languageServerWrapper.getServerDefinition().getLanguageId(file, languageServerWrapper.getProject());
        textDocument.setLanguageId(languageId);
        textDocument.setVersion(++version);
        didOpenFuture = languageServerWrapper
                .getInitializedServer()
                .thenApplyAsync(ls -> {
                    ls.getTextDocumentService()
                            .didOpen(new DidOpenTextDocumentParams(textDocument));
                    return ls;
                })
                .thenCompose(ls ->
                        CompletableFuture.supplyAsync(() -> {
                            if (!ApplicationManager.getApplication().isUnitTestMode()) {
                                try {
                                    // Wait 500ms after sending didOpen notification
                                    // to be sure that the notification has been sent before
                                    // consuming other LSP request like 'textDocument/codeLens'.
                                    TimeUnit.MILLISECONDS.sleep(WAIT_AFTER_SENDING_DID_OPEN);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                            return ls;
                        })
                );
        processPullDiagnosticIfNeeded(didOpenFuture, version);
        return didOpenFuture;
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        DocumentListener.super.documentChanged(event);
        var syncKind = getTextDocumentSyncKind();
        if (syncKind == TextDocumentSyncKind.None) {
            return;
        }
        if (syncKind == TextDocumentSyncKind.Full) {
            synchronized (changeEvents) {
                changeEvents.clear();
                changeEvents.add(createChangeEvent(event));
            }
        }

        if (ApplicationManager.getApplication().isUnitTestMode()) {
            sendDidChangeEvents();
        } else {
            Project project = languageServerWrapper.getProject();
            PsiDocumentManager.getInstance(project)
                    .performForCommittedDocument(event.getDocument(), this::sendDidChangeEvents);
        }
    }

    private void sendDidChangeEvents() {
        List<TextDocumentContentChangeEvent> events;
        synchronized (changeEvents) {
            if (changeEvents.isEmpty()) {
                // Don't send didChange notification with empty contentChanges.
                return;
            }
            events = new ArrayList<>(changeEvents);
            changeEvents.clear();
        }

        final int version = ++this.version;
        // send 'textDocument/didChange' notification
        DidChangeTextDocumentParams changeParamsToSend = new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(), events);
        changeParamsToSend.getTextDocument().setUri(fileUri);
        changeParamsToSend.getTextDocument().setVersion(version);
        var didChange = languageServerWrapper.sendNotification(ls -> {
            ls.getTextDocumentService().didChange(changeParamsToSend);
            return ls;
        });
        processPullDiagnosticIfNeeded(didChange, version);
    }

    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent event) {
        if (getTextDocumentSyncKind() == TextDocumentSyncKind.Incremental) {
            // this really needs to happen before event gets actually
            // applied, to properly compute positions
            synchronized (changeEvents) {
                changeEvents.add(createChangeEvent(event));
            }
        }
    }

    private TextDocumentContentChangeEvent createChangeEvent(DocumentEvent event) {
        Document document = event.getDocument();
        TextDocumentSyncKind syncKind = getTextDocumentSyncKind();
        switch (syncKind) {
            case None:
                return null;
            case Full: {
                TextDocumentContentChangeEvent changeEvent = new TextDocumentContentChangeEvent();
                changeEvent.setText(event.getDocument().getText());
                return changeEvent;
            }
            case Incremental: {
                TextDocumentContentChangeEvent changeEvent = new TextDocumentContentChangeEvent();
                CharSequence newText = event.getNewFragment();
                int offset = event.getOffset();
                int length = event.getOldLength();
                try {
                    // try to convert the Eclipse start/end offset to LS range.
                    Range range = new Range(LSPIJUtils.toPosition(offset, document),
                            LSPIJUtils.toPosition(offset + length, document));
                    changeEvent.setRange(range);
                    changeEvent.setText(newText.toString());
                    changeEvent.setRangeLength(length);
                } catch (Exception e) {
                    // error while conversion (should never occur)
                    // set the full document text as changes.
                    changeEvent.setText(document.getText());
                }
                return changeEvent;
            }
        }
        return null;
    }

    public void documentSaved() {
        var saveOptions = syncOptions.getSave();
        if (saveOptions == null || (saveOptions.isLeft() && !saveOptions.getLeft()) || (saveOptions.isRight() && saveOptions.getRight() == null)) {
            // Don't send textDocument/didSave
            return;
        }

        // Send textDocument/didSave
        languageServerWrapper.sendNotification(ls -> {
            TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileUri);
            boolean includedText = saveOptions.isRight() && saveOptions.getRight().getIncludeText() != null && saveOptions.getRight().getIncludeText();
            DidSaveTextDocumentParams params = new DidSaveTextDocumentParams(identifier, includedText ? document.getText() : null);
            ls.getTextDocumentService().didSave(params);
            return ls;
        });
    }

    public void documentClosed() {
        // When LS is shut down all documents are being disconnected. No need to send "didClose" message to the LS that is being shut down or not yet started
        if (syncOptions.getOpenClose() && languageServerWrapper.isActive()) {
            // Ensure pending textDocument/didChange events are sent before the file is closed.
            //
            // This handles the specific case where a file is being renamed:
            // the original file is closed (triggering a textDocument/didClose),
            // but workspace/willRenameFiles may modify the file before that.
            // Without this call, the didChange notification could be sent
            // after didClose, breaking the expected LSP event order.
            if (!changeEvents.isEmpty()) {
                sendDidChangeEvents();
            }

            // Send textDocument/didClose
            languageServerWrapper.sendNotification(ls -> {
                TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileUri);
                DidCloseTextDocumentParams params = new DidCloseTextDocumentParams(identifier);
                ls.getTextDocumentService().didClose(params);
                return ls;
            });
        }
    }

    /**
     * Returns the text document sync kind capabilities of the server and {@link TextDocumentSyncKind#Full} otherwise.
     *
     * @return the text document sync kind capabilities of the server and {@link TextDocumentSyncKind#Full} otherwise.
     */
    private TextDocumentSyncKind getTextDocumentSyncKind() {
        return syncOptions.getChange();
    }

    /**
     * Returns the document.
     *
     * @return the document.
     */
    public @NotNull Document getDocument() {
        return this.document;
    }

    /**
     * Returns the current version of the LSP {@link TextDocumentItem}.
     *
     * @return the current version of the LSP {@link TextDocumentItem}.
     */
    int getVersion() {
        return version;
    }

    @Override
    public void dispose() {
        documentClosed();
    }

    /**
     * Refresh pull diagnostic according the given origin.
     *
     * @param origin the origin (call by 'textDocument/diagnostic' by dynamic registerCapability or by 'workspace/diagnostic/refresh')
     */
    public void refreshPullDiagnostic(@NotNull RefreshPullDiagnosticOrigin origin) {
        int currentVersion = version;
        if (origin == RefreshPullDiagnosticOrigin.ON_REGISTER_CAPABILITY) {
            // called by 'textDocument/diagnostic' by dynamic registerCapability
            // we need to consume the 'textDocument/diagnostic' if when didOpen has occurred, the pull diagnostic
            // capability was not enabled.
            if (!isDiagnosticNotPulledOnDidOpen()) {
                // the didOpen have already consumed the 'textDocument/diagnostic', do nothing
                return;
            }
        } else if (origin == RefreshPullDiagnosticOrigin.ON_WORKSPACE_REFRESH) {
            // called by 'workspace/diagnostic/refresh'
            // Set version to -1 to force the call of the pull diagnostic even if version has not changed.
            currentVersion = -1;
        }
        processPullDiagnosticIfNeeded(null, currentVersion);
    }

    /**
     * Process LSP pull diagnostic if the language server supports it.
     *
     * @param didFuture the didOpen, didChange future.
     * @param version   the current document version.
     */
    private void processPullDiagnosticIfNeeded(@Nullable CompletableFuture<LanguageServer> didFuture,
                                               int version) {
        boolean debounceValidation = didFuture != null && !ApplicationManager.getApplication().isUnitTestMode();
        if (!isPullDiagnosticsSupported()) {
            // The language server doesn't support pull diagnostics.
            diagnosticNotPulledOnDidOpen = didFuture != null && didFuture == didOpenFuture;
            return;
        }
        if (!debounceValidation) {
            // Refresh pull diagnostic without debounce
            if (version != -1 && version != this.version) {
                // The document has changed, do nothing
                return;
            }
            if (didOpenFuture == null) {
                return;
            }
            var ls = didOpenFuture.getNow(null);
            if (ls == null) {
                // The didOpen is not finished, refresh the pull diagnostic when server is ready
                didOpenFuture.
                        thenAccept(readyLs -> refreshPullDiagnostic(version, readyLs));
            } else {
                // The server is ready, refresh the pull diagnostics
                refreshPullDiagnostic(version, ls);
            }
            return;
        }
        // Refresh pull diagnostic with debounce (after a didOpen , didChange).
        // Initialize the Alarm which consumes 'textDocument/diagnostic' with debounce mode.
        if (debouncePullDiagnosticsAlarm == null) {
            debouncePullDiagnosticsAlarm = getDebouncePullDiagnosticsAlarm();
        } else {
            debouncePullDiagnosticsAlarm.cancelAllRequests();
        }
        didFuture
                .thenApply(ls -> {
                    // didOpen, didChange notification has been sent, consume 'textDocument/diagnostic' with debounce mode.
                    debouncePullDiagnosticsAlarm.addRequest(() -> {
                        if (version != -1 && version != this.version) {
                            // The document has changed, do nothing
                            return;
                        }
                        refreshPullDiagnostic(version, ls);
                    }, 500);
                    return ls;
                });
    }

    private void refreshPullDiagnostic(int version, @NotNull LanguageServer ls) {
        // Consume 'textDocument/diagnostic'
        DocumentDiagnosticParams params = new DocumentDiagnosticParams();
        params.setIdentifier(getPullIdentifier());
        params.setTextDocument(new TextDocumentIdentifier(fileUri));
        ls.getTextDocumentService()
                .diagnostic(params)
                .thenAcceptAsync(diagnosticReport -> {
                    if (diagnosticReport == null || (version != -1 && version != this.version)) {
                        // The document has changed, do nothing
                        return;
                    }
                    var clientFeatures = languageServerWrapper.getClientFeatures();
                    // Update the diagnostics cache from the opened file and refresh UI to process LSPDiagnosticAnnotator.
                    if (diagnosticReport.isLeft()) {
                        RelatedFullDocumentDiagnosticReport fileReport = diagnosticReport.getLeft();
                        // Update the diagnostics cache from the opened file
                        updatePullDiagnostics(file, fileReport);
                    } else if (diagnosticReport.isRight()) {
                        RelatedUnchangedDocumentDiagnosticReport allFilesReport = diagnosticReport.getRight();
                        Map<String, Either<FullDocumentDiagnosticReport, UnchangedDocumentDiagnosticReport>> relatedDocuments = allFilesReport.getRelatedDocuments();
                        if (relatedDocuments != null) {
                            for (var relatedDocument : relatedDocuments.entrySet()) {
                                String documentUri = relatedDocument.getKey();
                                Either<FullDocumentDiagnosticReport, UnchangedDocumentDiagnosticReport> fileReport = relatedDocument.getValue();
                                if (fileReport != null) {
                                    if (fileReport.isLeft()) {
                                        VirtualFile file = FileUriSupport.findFileByUri(documentUri, clientFeatures);
                                        if (file != null) {
                                            updatePullDiagnostics(file, fileReport.getLeft());
                                        }
                                    } else if (fileReport.isRight()) {
                                        // TODO: implement UnchangedDocumentDiagnosticReport
                                    }
                                }
                            }
                        }
                    }

                });
    }

    private void updatePullDiagnostics(@NotNull VirtualFile file,
                                       @NotNull FullDocumentDiagnosticReport diagnosticReport) {
        // Update diagnostics for the opened/closed document
        List<Diagnostic> diagnostics = diagnosticReport.getItems() != null ? diagnosticReport.getItems() : Collections.emptyList();
        String identifier = getPullIdentifier();
        if (identifier == null) {
            identifier = LSPDocumentBase.PULL_DIAGNOSTIC_IDENTIFIER;
        }
        languageServerWrapper.updateDiagnostics(file, identifier, diagnostics);
    }

    @Nullable
    private String getPullIdentifier() {
        var clientFeatures = languageServerWrapper.getClientFeatures();
        return clientFeatures.getDiagnosticFeature().getDiagnosticIdentifier();
    }

    public boolean isPullDiagnosticsSupported() {
        return languageServerWrapper.getClientFeatures().getDiagnosticFeature().isDiagnosticSupported(file);
    }

    private Alarm getDebouncePullDiagnosticsAlarm() {
        if (debouncePullDiagnosticsAlarm == null) {
            synchronized (this) {
                if (debouncePullDiagnosticsAlarm == null) {
                    debouncePullDiagnosticsAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
                }
            }
        }
        return debouncePullDiagnosticsAlarm;
    }

    private boolean isDiagnosticNotPulledOnDidOpen() {
        return diagnosticNotPulledOnDidOpen;
    }
}
