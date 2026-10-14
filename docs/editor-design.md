# Editor direction

Owner brief: a mobile motion editor with Alight Motion's workflow, After Effects' graph controls, Flow-style curve presets, face pose tracking, and a Null that drives a 3D camera.

Reading this as a composition workspace for mobile motion designers, with a compact creative-tool language. ENERGY 2 / RHYTHM 2 / MOTION 1.

- Keep the existing orange identity (#FF6A2B) for the playhead, selected handles and primary actions; footage supplies the other colours.
- Use graphite (#141416), raised slate (#27272C), readable white (#EDEDEF), secondary grey (#C0C0CA) and subdued text (#AAAAB5); a fixed dark workspace helps judge footage without bright surrounding chrome.
- Use Android's native sans for controls. Time, values, rulers and compact metadata use tabular monospace so short changing fields remain aligned.
- Make the composition the focal point, with a compact workspace strip, transport, timeline and a labelled inspector. Do not spend portrait space on a second action row.
- Portrait: a smaller preview leaves room for properties; the graph replaces the preview when opened and keeps transport/timeline context. Short landscape: preview and timeline share the left column while the inspector occupies the right column. Tablet: persistent inspector beside the composition.
- Inspector headers use 8 dp corners; preview uses 4 dp corners; primary actions use 8 dp corners. Distinguish controls from modal surfaces.
- Use plain selected backgrounds and labels. Graph grids communicate measured time/value; they are not decorative backgrounds.
- Tap targets are at least 44 dp. Numerical graph controls provide an alternative to precision dragging.
- Short fades and selection feedback communicate screen changes. Press feedback indicates a control activation; thumbnails stay still unless explicitly played. Product animation belongs in the composition.
- Keep real counts, offline processing status and actionable error text. Face depth is a relative estimate, not a metric reconstruction or a face mesh.

Plan critique: retain the owner's existing orange identity, rather than changing the palette for novelty. The meaningful change is the workspace structure and curve interaction, with shorter chrome and accessible numeric controls.

Native Material icons identify actions such as transforms, graph, parent links and effects. The effect wand refers to rendered image treatments; it is not a claim of AI generation. Grid lines belong only to measured graph/ruler coordinates or explicitly enabled composition guides. Curves and diamond keys repeat the editor's animation vocabulary. The existing layer names and footage remain the content of the workspace; no invented customer or usage data is displayed.

The bottom toolbar scrolls as a navigation strip, and its items widen with font scale. Labels remain readable at large font settings while the selected tool remains visible. Layer and keyframe menus provide native keyboard and accessibility alternatives to precise canvas selection. Numeric dialogs reject non-finite input and close with Escape. The renderer skips zero-sized layout frames during keyboard transitions.

Safe drawing insets keep controls clear of status/navigation bars and cutouts. In short landscape, workspace actions join the top bar so the graph still has room. Effect categories scroll with the results in landscape, while the search and effects/preset selector remain reachable. Search clears focus before closing the keyboard; typing temporarily removes nonessential browser chrome.
