#!/usr/bin/env python3
"""Apply the 31 missing-method patches to the FongMi/media checkout
(release-1.11.0-fongmi) so the TV app code compiles against it.
See stub-plan.md §4. Idempotent: re-running skips already-applied patches.
Usage: python3 patch_media.py [media_root]   (default: ../media)
Exits non-zero if any anchor is missing.
"""
import re
import sys
from pathlib import Path

MEDIA = Path(sys.argv[1] if len(sys.argv) > 1 else "../media").resolve()
FAILED = []


def read(rel: str) -> str:
    return (MEDIA / rel).read_text()


def write(rel: str, text: str) -> None:
    (MEDIA / rel).write_text(text)


def ensure_import(rel: str, import_line: str) -> None:
    """Add `import ...;` after the last import with the same package prefix,
    or after the last import overall. No-op if already present."""
    text = read(rel)
    if import_line in text:
        return
    pkg = import_line.split()[1].rsplit(".", 1)[0]  # e.g. androidx.media3.common.text
    imports = [m for m in re.finditer(r"^import .+;$", text, re.M)]
    anchor = None
    for m in imports:
        if m.group(0).startswith(f"import {pkg}."):
            anchor = m.group(0)
    if anchor is None:
        anchor = imports[-1].group(0)
    text = text.replace(anchor, anchor + "\n" + import_line, 1)
    write(rel, text)
    print(f"  {rel}: + {import_line}")


def insert_after(rel: str, anchor: str, code: str, guard: str) -> None:
    """Insert code right after the first occurrence of anchor. Skips if guard
    already present; fails loudly if anchor is missing."""
    text = read(rel)
    if guard in text:
        print(f"  {rel}: skip (already has {guard.strip()[:40]})")
        return
    if anchor not in text:
        FAILED.append(f"{rel}: anchor not found: {anchor[:80]!r}")
        return
    text = text.replace(anchor, anchor + "\n" + code, 1)
    write(rel, text)
    print(f"  {rel}: + {guard.strip()[:60]}")


def insert_before(rel: str, anchor: str, code: str, guard: str) -> None:
    """Insert code right before the first occurrence of anchor. Skips if guard
    already present; fails loudly if anchor is missing."""
    text = read(rel)
    if guard in text:
        print(f"  {rel}: skip (already has {guard.strip()[:40]})")
        return
    if anchor not in text:
        FAILED.append(f"{rel}: anchor not found: {anchor[:80]!r}")
        return
    text = text.replace(anchor, code + "\n" + anchor, 1)
    write(rel, text)
    print(f"  {rel}: + {guard.strip()[:60]}")


def class_end_offset(text: str, class_decl_re: str) -> int:
    """Offset of the closing brace matching the class declaration."""
    m = re.search(class_decl_re, text, re.M)
    assert m, f"class decl not found: {class_decl_re}"
    i = text.index("{", m.end() - 1)
    depth = 0
    j = i
    n = len(text)
    while j < n:
        c = text[j]
        if c == "/" and j + 1 < n and text[j + 1] == "/":
            j = text.find("\n", j)
            if j == -1:
                break
            continue
        if c == "/" and j + 1 < n and text[j + 1] == "*":
            k = text.find("*/", j + 2)
            j = n if k == -1 else k + 2
            continue
        if c == '"':
            j += 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            j += 1
            continue
        if c == "'":
            j += 1
            while j < n and text[j] != "'":
                j += 2 if text[j] == "\\" else 1
            j += 1
            continue
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return j
        j += 1
    raise AssertionError("unbalanced braces")


def insert_before_class_end(rel: str, class_decl_re: str, code: str, guard: str) -> None:
    text = read(rel)
    if guard in text:
        print(f"  {rel}: skip (already has {guard.strip()[:40]})")
        return
    end = class_end_offset(text, class_decl_re)
    text = text[:end] + code + "\n" + text[end:]
    write(rel, text)
    print(f"  {rel}: + {guard.strip()[:60]} (before class end)")


P = "libraries/common/src/main/java/androidx/media3/common/Player.java"
PV = "libraries/ui/src/main/java/androidx/media3/ui/PlayerView.java"
MP = "libraries/mpvplayer/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java"
DE = "libraries/extractor/src/main/java/androidx/media3/extractor/DefaultExtractorsFactory.java"

# ---------- 4.1 Player.java (4 methods) ----------
print("Player.java")
ensure_import(P, "import androidx.media3.common.text.SubtitleOffsets;")
insert_after(
    P,
    "  @UnstableApi\n"
    "  default long getTextOffsetMs() {\n"
    "    throw new UnsupportedOperationException();\n"
    "  }\n",
    "\n"
    "  default DiscMenuState getDiscMenuState() {\n"
    "    return DiscMenuState.EMPTY;\n"
    "  }\n"
    "\n"
    "  default SubtitleOffsets getSubtitleOffsets() {\n"
    "    return SubtitleOffsets.ZERO;\n"
    "  }\n"
    "\n"
    "  default void setSubtitleOffsets(SubtitleOffsets offsets) {}\n",
    "getDiscMenuState()",
)
insert_after(
    P,
    "    default void onPlaybackStateChanged(@State int playbackState) {}\n",
    "\n"
    "    default void onDiscMenuStateChanged(DiscMenuState state) {}\n",
    "onDiscMenuStateChanged(DiscMenuState state)",
)

# ---------- 4.2 PlayerView.java (7 methods + 4 fields) ----------
print("PlayerView.java")
ensure_import(PV, "import androidx.media3.common.DiscMenuController;")
ensure_import(PV, "import androidx.media3.common.VideoTransform;")
insert_after(
    PV,
    "  @Nullable private Player player;\n",
    "\n"
    "  private VideoTransform videoTransform = new VideoTransform(1.0f, 1.0f, 0.0f, 0.0f);\n"
    "  private float videoAspectRatioOverride;\n"
    "  @Nullable private DiscMenuController discMenuController;\n"
    "  @Nullable private Player backendPlayer;\n",
    "private VideoTransform videoTransform",
)
insert_before_class_end(
    PV,
    r"^public class PlayerView extends FrameLayout implements AdViewProvider \{$",
    "\n"
    "  public VideoTransform getVideoTransform() {\n"
    "    return videoTransform;\n"
    "  }\n"
    "\n"
    "  public void setVideoTransform(VideoTransform transform) {\n"
    "    this.videoTransform = transform;\n"
    "  }\n"
    "\n"
    "  public float getVideoAspectRatioOverride() {\n"
    "    return videoAspectRatioOverride;\n"
    "  }\n"
    "\n"
    "  public void setVideoAspectRatioOverride(float aspectRatio) {\n"
    "    this.videoAspectRatioOverride = aspectRatio;\n"
    "  }\n"
    "\n"
    "  public void setDiscMenuController(@Nullable DiscMenuController controller) {\n"
    "    this.discMenuController = controller;\n"
    "  }\n"
    "\n"
    "  public void setBackendPlayer(Player player) {\n"
    "    this.backendPlayer = player;\n"
    "  }\n"
    "\n"
    "  public boolean dispatchDiscMenuTouchEvent(MotionEvent event) {\n"
    "    return false;\n"
    "  }\n",
    "getVideoTransform()",
)

# ---------- 4.3 MpvPlayer.java (19 methods) ----------
print("MpvPlayer.java")
text = read(MP)
if "implements DiscMenuController" not in text:
    old = "public final class MpvPlayer extends SimpleBasePlayer {"
    assert old in text, "MpvPlayer class decl anchor missing"
    write(MP, text.replace(old, "public final class MpvPlayer extends SimpleBasePlayer implements DiscMenuController {", 1))
    print("  MpvPlayer.java: class decl += implements DiscMenuController")
for imp in (
    "import androidx.media3.common.DiscMenuController;",
    "import androidx.media3.common.TrackSelectionOverride;",
    "import androidx.media3.common.text.SubtitleContent;",
    "import androidx.media3.common.text.SubtitleSelectionState;",
    "import androidx.media3.common.text.SubtitleOffsets;",
    "import androidx.media3.datasource.DataSource;",
    "import java.util.function.Consumer;",
):
    ensure_import(MP, imp)
insert_before(
    MP,
    "  public static final class Builder {\n",
    "  @Override\n"
    "  public boolean open(String action, Consumer<DiscMenuController.OpenResult> callback) {\n"
    "    return false;\n"
    "  }\n"
    "\n"
    "  @Override\n"
    "  public boolean isActive() {\n"
    "    return false;\n"
    "  }\n"
    "\n"
    "  @Override\n"
    "  public boolean isNavigationPlayback() {\n"
    "    return false;\n"
    "  }\n"
    "\n"
    "  @Override\n"
    "  public boolean sendAction(String action) {\n"
    "    return false;\n"
    "  }\n"
    "\n"
    "  @Override\n"
    "  public boolean supportsPointer() {\n"
    "    return false;\n"
    "  }\n"
    "\n"
    "  public SubtitleSelectionState getSubtitleSelectionState() {\n"
    "    return SubtitleSelectionState.EMPTY;\n"
    "  }\n"
    "\n"
    "  public SubtitleContent getSubtitleContentForSelection(TrackSelectionOverride selection) {\n"
    "    return SubtitleContent.UNSUPPORTED;\n"
    "  }\n"
    "\n"
    "  public void setSubtitleContentEnabled(boolean enabled) {}\n"
    "\n"
    "  public boolean canRetrySubtitleContent(Format format) {\n"
    "    return false;\n"
    "  }\n"
    "\n"
    "  public void retrySubtitleContent(Format format) {}\n"
    "\n"
    "  public void setSecondaryTextTrackSelectionOverride(\n"
    "      @Nullable TrackSelectionOverride selection) {}\n"
    "\n"
    "  public void setSecondaryTextTrackAutoSelectionEnabled(boolean enabled) {}\n"
    "\n"
    "  public void resetSecondaryTextTrackSelection() {}\n"
    "\n"
    "  public MpvDecoderMode getVideoDecoderMode() {\n"
    "    return MpvDecoderMode.AUTO;\n"
    "  }\n"
    "\n"
    "  public void setVideoDecoderMode(MpvDecoderMode mode) {}\n"
    "\n"
    "  public void setVerboseLoggingEnabled(boolean enabled) {}\n"
    "\n"
    "  public void setDiscDataSourceFactory(DataSource.Factory dataSourceFactory) {}\n"
    "\n"
    "  public void setSubtitleContentLoader(MpvSubtitleContentLoader loader) {}\n",
    "getSubtitleSelectionState()",
)
insert_after(
    MP,
    "    public MpvPlayer build() {\n"
    "      checkState(!buildCalled);\n"
    "      buildCalled = true;\n"
    "      return new MpvPlayer(context, config, decode, looper);\n"
    "    }\n",
    "\n"
    "    public Builder setVideoDecoderMode(MpvDecoderMode mode) {\n"
    "      return this;\n"
    "    }\n",
    "public Builder setVideoDecoderMode",
)

# ---------- 4.4 DefaultExtractorsFactory.java (1 method) ----------
print("DefaultExtractorsFactory.java")
insert_before_class_end(
    DE,
    r"^public final class DefaultExtractorsFactory implements ExtractorsFactory \{$",
    "\n"
    "  public synchronized DefaultExtractorsFactory setMp4ClearKeys(\n"
    "      Map<String, String> mp4ClearKeys) {\n"
    "    return this;\n"
    "  }\n",
    "setMp4ClearKeys(",
)

if FAILED:
    print("FAILED anchors:")
    for f in FAILED:
        print(" -", f)
    sys.exit(1)
print("All media patches applied.")
