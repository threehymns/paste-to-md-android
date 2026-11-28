# Paste to Markdown

Local Android utility that turns rich HTML from the system clipboard into editable CommonMark Markdown.

## Language

**Screen Stack**:
The ordered history of open screens in the app (Paste, Preview, Settings) supporting interruptible forward, back, and predictive-back transitions.
_Avoid_: Router, backstack list, screen state

**Primary Action Anchor**:
The primary button on a screen (Paste on the Paste screen, Copy Markdown on the Preview screen) whose resting bounds and press-release motion carry across a **Screen Stack** transition.
_Avoid_: Morph target, shared button, CTA slot

**Morph Overlay**:
The transient mid-flight button rendered above transitioning screens as one **Primary Action Anchor** transforms into another; omitted when reduced motion is enabled.
_Avoid_: Floating button, shared-element overlay, transition button

**Conversion Session**:
An active clipboard-to-Markdown editing lifecycle covering paste intake, HTML conversion, editable Markdown text, copy-back, and file export, ending when the Preview screen leaves the **Screen Stack**.
_Avoid_: Session view model, paste handler, clipboard controller
