# Scanner scale fix (v0.4.3)

Problem: the old contour tracker could choose a single square sticker because Canny/approxPolyDP had no semantic understanding of cube scale.

Fix:
- fixed white 3x3/5x5 guide is the only capture/sample coordinate system;
- AR contour is decorative/assistive only;
- candidate contour must be macro-sized relative to the guide;
- candidate side lengths must be large enough;
- candidate center must stay near the guide center;
- if those conditions fail, AR simply disappears and fixed-grid scanning continues normally;
- live color chips and uncertainty cells are drawn on the fixed guide, matching exactly what Capture reads.
