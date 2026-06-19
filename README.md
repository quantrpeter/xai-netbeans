# xAI Assistant for NetBeans

![](/img/banner1.png)

A GitHub Copilot–style coding assistant for Apache NetBeans, powered by the
[xAI API](https://docs.x.ai) (Grok models). Enter a prompt and the assistant can
explain your code, plan changes, debug issues, and — in Agent mode — read,
create, and edit files in your project.

## Modes

| Mode | Tools | Edits files? | Use it for |
|------|-------|--------------|------------|
| **Ask** | read-only | No | Questions about the codebase |
| **Agent** | full | Yes | "Implement X" — explores and edits files end to end |
| **Plan** | read-only | No | Produce a step-by-step implementation plan |
| **Debug** | read-only | No | Investigate a bug and propose a root-cause fix |
| **Multitask** | full | Yes | Run several Agent sessions in parallel tabs |

Multitasking is achieved by opening multiple session tabs in the assistant
window (the **New Task** / **New Ask** buttons); each tab is an independent
conversation that can run concurrently.

## Build

Requires Maven and a JDK 17+ (tested building with JDK 25, cross-compiled to
release 17). The module targets NetBeans `RELEASE240` (NetBeans 24).

```bash
mvn clean package
```

This produces `target/xai-netbeans-1.0.nbm`.

## Install

In NetBeans: **Tools ▸ Plugins ▸ Downloaded ▸ Add Plugins…**, select
`target/xai-netbeans-1.0.nbm`, then install and restart.

Alternatively, run the module directly from sources with `mvn nbm:run-ide`.

## Configure

Open **Tools ▸ Options ▸ Miscellaneous ▸ xAI Assistant** and set:

- **xAI API key** — your `api.x.ai` key (or set the `XAI_API_KEY` environment
  variable instead).
- **API base URL** — defaults to `https://api.x.ai/v1`.
- **Model** — defaults to `grok-code-fast-1`.
- **Temperature**, **Max agent steps**.
- **Workspace root** — optional; otherwise the open project's directory is used
  to resolve relative file paths.
- **Ask before editing** — when on, the agent asks for confirmation before
  creating or modifying any file.

## Use

Open the window via **Window ▸ xAI Assistant**. Pick a mode, type a prompt, and
press **Ctrl+Enter** (or **Send**). Tool activity (file reads, edits, searches)
is shown inline in the transcript. Use **Stop** to cancel a running turn.

## Architecture

```
org.hkprog.xai.netbeans
├── api/        XaiClient + chat/tool-call data model (java.net.http + Gson)
├── settings/   XaiSettings (NbPreferences) + Options panel
├── tools/      AgentTool implementations (read/list/search/write/edit) + registry + Workspace
├── core/       Mode, SystemPrompts, AgentEngine (the tool-calling loop)
└── ui/         XaiAssistantTopComponent + SessionPanel + Transcript
```

The client calls the OpenAI-compatible `POST {baseUrl}/chat/completions`
endpoint. In modes that allow it, the model invokes function-calling tools to
inspect and modify the workspace; the `AgentEngine` runs that
request → tool-call → tool-result loop until the model returns a final answer
or the step cap is reached.

[ARCHITECTURE.md](/ARCHITECTURE.md)

## Notes & limitations

- Responses are non-streaming in this version; a turn shows once complete.
- Mutating tools (`write_file`, `edit_file`) are only available in Agent /
  Multitask modes and are gated by the approval setting.
- File-path resolution prefers the configured workspace root, then open NetBeans
  projects, then the IDE working directory.
