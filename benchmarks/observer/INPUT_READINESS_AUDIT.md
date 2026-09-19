# Input readiness audit, 2026-09-10

The FO candidate preflight recorded resource-manager completion to `frame_ready`
gaps of approximately 2.0 seconds. Protocol 2 required the raw overlay to be null.
That condition was too conservative for a transformed input/render path.

Authority inspected:

- Supplied Minecraft 26.1 decompiled source: `KeyboardHandler.charTyped`,
  `MouseHandler` dispatch methods, `Minecraft.tick/handleKeybinds`,
  `LoadingOverlay.tick/extractRenderState`, and `GameRenderer` extraction/render.
- Official cached 26.2 client classes: corresponding method signatures and
  character-dispatch bytecode; the GUI ownership move is handled by the adapter.
- Installed `rrls-5.2.5+mc.26.1.jar`, specifically its
  `RendererKeyboardMouseMixin`, `MinecraftClientMixin`, `LoadingOverlayMixin`,
  `TitleScreenMixin`, and `OverlayHelper`. Project source location advertised by
  the installed metadata: https://github.com/dima-dencep/rrls.

RRLS substitutes a nonblocking overlay result at input/render call sites while
the raw Minecraft overlay object remains allocated. Vanilla retires its loading
overlay after the fade interval. Reading the raw field therefore cannot tell
whether the menu is already usable. No Quick Pack implementation was inspected.

Protocol 3 observes the actual dispatch route. The neutral controller invokes
`KeyboardHandler.charTyped` with NUL after a newly extracted/presented menu frame.
It never invokes Screen directly. An injection after the real Screen call
acknowledges the event only if the transformed window/screen/overlay gates let it
through. NUL is not allowed chat text, carries no key-binding state, and the
inspected character handler does not notify the vanilla idle timer. A separate
hook rejects any transformed probe that calls the idle timer. It does not
suppress or restore timestamps. Thus probe-based activity cannot qualify a run.

The in-world controller emits no input. It observes Minecraft's normal
`handleKeybinds` return after a new world generation has rendered. Menu and world
input acknowledgments are separate and cannot satisfy each other's readiness.
The original focus, resize, throttle, successful-generation, and title-fade
checks remain active. `resources_rendered`, `input_ready`, and `overlay_cleared`
are separate milestones; a raw allocated overlay no longer adds false wait time
to `frame_ready` when real dispatch is already usable.

Limitations: no physical OS input or GPU fence is synthesized. This proves the
client's dispatch gate and completed rendering path, not screenshot equivalence,
input-device latency, shader quality, or all first-use assets. Those gates remain
separate. New injection selectors and timing milestones require runtime preflight
on each exact loader/version before qualifying measurements.
