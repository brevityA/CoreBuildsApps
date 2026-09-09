"""Core Builds UI Studio — TV screen generator for the icon packs.

Generates Leanback-style screens (layout XML + Activity + adapter + strings)
with correct D-pad wiring and smooth focus motion baked in, in the Core Builds
night-chrome language (Brand Guide v1.0 §05).

Entry points:
  python tools/build_ui.py --preset browser --name MyScreen
  python tools/validate_ui.py app/src/main/res/layout/activity_main.xml
  tools/ui/studio.html  (visual designer, open in any browser)
"""

__version__ = "1.0.0"
