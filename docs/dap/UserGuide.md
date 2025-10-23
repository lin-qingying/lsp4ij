# Debug Adapter Protocol

LSP4IJ provides [Debug Adapter Protocol](https://microsoft.github.io/debug-adapter-protocol/) support. 
You can read [the DAP Support overview](./DAPSupport.md), describing which DAP features are implemented, and how.
If you need to customize the DAP support you can [register your DAP server with extension point](./DeveloperGuide.md).

The DAP support is available with the `Debug Adapter Protocol` run/debug configuration type:

![DAP Configuration Type](./images/DAP_config_type.png)

After configuring the [DAP configuration type](#dap-configuration-type), you can debug your file.

Here is an example with `JavaScript debugging`, which uses the [VSCode JS Debug DAP server](./user-defined-dap/vscode-js-debug.md):

![DAP Configuration Type](./images/DAP_debugging_overview.png)

## DAP Configuration Type:

To configure debugging with DAP, you need to fill in:

### Server tab

The `Server` tab to specify the DAP server:

  ![DAP Configuration Type/Server](./images/DAP_config_type_server.png)

### Mappings tab

The `Mappings` tab to specify the files which can be debugged to allow adding/removing breakpoints:

![DAP Configuration Type/Mappings](./images/DAP_config_type_mappings.png)

### Configuration tab

The `Configuration` tab to specify the working directory and the file you want to run/debug:

  ![DAP Configuration Type/Configuration](./images/DAP_config_type_configuration.png)

## Breakpoints

### Adding breakpoint

When a file can be debugged using a DAP server specified in the [Mappings tab](#mappings-tab), a breakpoint can be added.

![DAP Breakpoint set](./images/DAP_breakpoint_set.png)

### Verify breakpoint

When the DAP server starts, breakpoints are sent to it, and it responds with the status of those breakpoints — 
whether they are validated or not.

For example, when the DAP server [VSCode JS Debug](./user-defined-dap/vscode-js-debug.md) starts, it invalidates all breakpoints.:

![DAP Breakpoint invalid](./images/DAP_breakpoint_invalid.png)

When the program starts, it checks and validates the breakpoints.

![DAP Breakpoint invalid](./images/DAP_breakpoint_checked.png)

### Conditional breakpoint

Conditional breakpoints are also supported. Here is an example of a conditional breakpoint written in JavaScript:

![DAP Conditional Breakpoint](./images/DAP_conditional_breakpoint.png)

### Exception breakpoint

Some DAP servers support exception breakpoints. If so, you must first run the configuration process, 
which starts the DAP server and retrieves the list of available exception breakpoints. 
This list is accessible through the `Exception Breakpoints` menu:

![DAP exception breakpoints](./images/DAP_exception_breakpoints.png)

The first time, the selected exception breakpoints 
are based on the default configuration provided by the DAP server.

You can then select or deselect the exception breakpoints you want to use.

Take a sample JavaScript file containing an error:

![DAP exception breakpoint / Syntax error](./images/DAP_exception_breakpoint_sample.png)

In this example, no breakpoints are defined. 
However, when you start the DAP server, it stops at the line with the line error:

![DAP exception breakpoint / Syntax error](./images/DAP_exception_breakpoint_sample_stop.png)

This happens because `Caught Exceptions` is selected.

## Inline value

The values of the variables are displayed inline, but this is not perfect because a DAP server generally cannot handle variable positions (only their values). 
To retrieve the variable positions, LSP4IJ uses the syntax highlighting information from the editor (TextMate or others).

Here a de demo with JavaScript:

![DAP inline value](./images/DAP_inline_value_demo.gif)

Theoretically, inline values should be handled by a language server via [textDocument/inlineValue](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlineValue)
but as no language servers seems implement this LSP request for the moment LSP4IJ doesn't use this strategy.

## Evaluate expression

Evaluate expression is available by consuming the [Evaluate request](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Evaluate) 

![Evaluate expression](./images/DAP_debugging_evaluate.png)

### Completion

If debug adapter [supports the `completions` request](https://microsoft.github.io/debug-adapter-protocol//specification.html#Types_Capabilities),
completion should be available in the expression editor by consuming the
[Completion request](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Completions):

![Completion](images/DAP_debugging_completion.png)

## Set value

If debug adapter [supports setting a variable to a value](https://microsoft.github.io/debug-adapter-protocol//specification.html#Types_Capabilities),
the `Set Value...` contextual menu should be available: 

![Set Value/Menu](images/DAP_debugging_setValue_menu.png)

You should edit the variable:

![Set Value/Edit](images/DAP_debugging_setValue_edit.png)

the edit apply will consume the
[SetVariable request](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_SetVariable):

## Run In Terminal

If the DAP server supports the [Run In Terminal](https://microsoft.github.io/debug-adapter-protocol/specification.html#Reverse_Requests_RunInTerminal) request, **LSP4IJ** can handle it automatically.  
This feature allows launching a debug session in either the `integrated terminal` or an `external terminal`.

Most DAP servers let you choose which terminal to use through the `console` field in the launch parameters:

- `"console": "integratedTerminal"` → Use the IDE's integrated terminal
- `"console": "externalTerminal"` → Open a separate external terminal window

The [VS Code JS Debug](./user-defined-dap/vscode-js-debug.md) adapter supports `Run In Terminal`. Below are two sample configurations:

### Integrated terminal

Here a sample with VSCode JS Debug and `integratedTerminal`:

```json
{
  "type": "pwa-node",
  "name": "Launch JavaScript file",
  "request": "launch",
  "program": "${file}",
  "cwd": "${workspaceFolder}",
  "console": "integratedTerminal"
}
```

![Run In Terminal integrated](./images/DAP_runInTerminal_integrated.png)

### External terminal

Here a sample with VSCode JS Debug and `externalTerminal`:

```json
{
  "type": "pwa-node",
  "name": "Launch JavaScript file",
  "request": "launch",
  "program": "${file}",
  "cwd": "${workspaceFolder}",
  "console": "externalTerminal"
}
```

![Run In Terminal external](./images/DAP_runInTerminal_external.png)

The `externalTerminal` is supported on **Windows** and **Linux**, but **not on macOS**.

## Disassemble

If the DAP server [supports disassembly](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Disassemble), open the Disassembly view from the stack trace context menu: `Open Disassembly View`.

![Open Disassembly View](./images/DAP_open_disassembly_view.png)

The Disassembly view shows the instructions for the current stack frame. You can add breakpoints and step through instructions one by one.

![Disassembly View](./images/DAP_disassembly_view.gif)

You can switch between source files and the Disassembly view at any time to continue step-by-step debugging.

Example: using [CodeLLDB](./user-defined-dap/codelldb.md) to debug a Rust executable built with `cargo build`.

![Disassembly demo](./images/DAP_disassembly_demo.gif)

## Contextual Menu

Click on right button open existing / new DAP run configuration:

![Run/Debug menu](images/DAP_contextual_menu.png)

## DAP server traces

If you wish to show DAP request/response traces when you will debug:

![Show DAP traces](./images/vscode-js-debug/traces_in_console.png)

you need to select `Trace` with `verbose`.

![Set verbose traces](./images/vscode-js-debug/set_traces.png)

## DAP settings

You can `create/remove/update` DAP servers with `Debug Adapter Protocol` entry:

![DAP settings](./images/DAP_settings.png)

## Templates

LSP4IJ provides DAP templates that allow to initialize a given DAP server very quickly:

- [CodeLLDB](./user-defined-dap/codelldb.md) which allows you to debug `Rust`,`Swift`, etc. files.
- [Go Delve DAP server](./user-defined-dap/go-delve.md) which allows you to debug `Go` files.
- [Julia DAP server](./user-defined-dap/julia.md) which allows you to debug `Julia` files. 
- [Python Debugpy DAP server](./user-defined-dap/python-debugpy.md) which allows you to debug `Python` files.
- [Ruby rdbg DAP server](./user-defined-dap/ruby-rdbg.md) which allows you to debug `Ruby` files.
- [Swift DAP Server](./user-defined-dap/swift-lldb.md) which allows you to debug `Swift` files.
- [VSCode JS Debug DAP Server](./user-defined-dap/vscode-js-debug.md) which allows you to debug `JavaScript/TypeScript` files.